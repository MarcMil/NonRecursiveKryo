package com.esotericsoftware.kryo.serializers;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.PatchedKryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.IntArray;

/**
 * A serializer which tries to not use recursion if possible.
 * @author Marc Miltenberger
 * @param <T>
 */
public class NonRecursiveSerializer<T> extends FieldSerializer<T> {

	private static final int INITIAL_WORKLIST_SIZE_WRITE = 64;
	private static final int INITIAL_WORKLIST_SIZE_READ = INITIAL_WORKLIST_SIZE_WRITE;
	private final FastStack<?> cachedWorklist = new FastStack<>(INITIAL_WORKLIST_SIZE_WRITE);

	public NonRecursiveSerializer(Kryo kryo, Class type) {
		super(kryo, type);
	}

	public NonRecursiveSerializer(Kryo kryo, Class type, FieldSerializerConfig config) {
		super(kryo, type, config);
	}

	@Override
	protected int pushTypeVariables() {
		return 0;
	}

	@Override
	protected void popTypeVariables(int pop) {
	}

	@Override
	public T read(Kryo ckryo, Input input, Class<? extends T> ctype) {
		try {
			FastStack<ReadWorklistItem> worklist = (FastStack<ReadWorklistItem>) this.cachedWorklist;
			if (!worklist.isEmpty())
				worklist = new FastStack<>(INITIAL_WORKLIST_SIZE_READ);

			PatchedKryo kryo = (PatchedKryo) ckryo;
			IntArray readReferenceIds = kryo.getReadReferenceIds();
			worklist.push(new ReadWorklistItem(this, ctype));
			Worklist: while (true) {
				ReadWorklistItem c = worklist.peek();

				final Class<?> type = c.classType;
				Object object = c.objectCreated;
				if (object == null) {
					object = create(kryo, input, (Class) type);
					kryo.reference(object);
					c.objectCreated = object;
				}

				CachedField[] fields = c.serializer.getFields();
				final int n = fields.length;
				for (int i = c.fieldIndex; i < n; i++) {
					CachedField f = fields[i];
					if (f instanceof ReflectField) {
						ReflectField field = (ReflectField) f;

						Class concreteType = field.resolveFieldClass();
						Serializer serializer = field.serializer;
						boolean canBeNull = field.canBeNull;
						if (concreteType == null) {
							// The concrete type of the field is unknown, read the class first.
							Registration registration = kryo.readClass(input);
							if (registration == null) {
								field.set(object, null);
								continue;
							}
							if (serializer == null)
								serializer = registration.getSerializer();
							concreteType = registration.getType();
							canBeNull = false;
						} else {
							if (serializer == null) {
								serializer = kryo.getSerializer(concreteType);
								// The concrete type of the field is known, always use the same serializer.
								if (field.valueClass != null && field.reuseSerializer)
									field.serializer = serializer;
							}
						}
						int stackSize = kryo.readReferenceOrNull(input, concreteType, canBeNull);
						if (stackSize == PatchedKryo.REF) {
							Object ro = kryo.getReadObject();

							if (ro == null && !canBeNull)
								throw new AssertionError("Should not happen.");

							field.set(object, ro);
							continue;
						}

						if (serializer instanceof NonRecursiveSerializer) {
							// YES!!
							// Add to worklist
							c.fieldIndex = i + 1;
							worklist.push(new ReadWorklistItem((NonRecursiveSerializer<?>) serializer, concreteType,
									object, field, stackSize));

							continue Worklist;
						} else {
							// :( Recursion incoming
							Object value = serializer.read(kryo, input, concreteType);
							field.set(object, value);
							if (stackSize == readReferenceIds.size)
								kryo.reference(value);
						}

					} else
						// These are "simple"
						f.read(input, object);
				}
				ReadWorklistItem popped = worklist.pop();
				if (popped != c)
					throw new AssertionError(
							"Workist entry after pop is different from the current one - this should not happen.");
				if (c.fieldTarget != null)
					c.fieldTarget.set(c.objectTarget, object);
				if (worklist.isEmpty())
					return (T) object;

			}
		} catch (IllegalAccessException e) {
			throw new KryoException(e);
		}
	}

	private static class ReadWorklistItem {
		public Object objectCreated;
		public ReflectField fieldTarget;
		public Object objectTarget;
		Class<?> classType;
		NonRecursiveSerializer<?> serializer;
		int fieldIndex;

		public ReadWorklistItem(NonRecursiveSerializer<?> s, Class<?> c) {
			this(s, c, null, null, Integer.MIN_VALUE);
		}

		public ReadWorklistItem(NonRecursiveSerializer<?> s, Class<?> c, Object objectTarget, ReflectField fieldTarget,
				int stackSize) {
			if (c == null)
				throw new IllegalArgumentException("Class cannot be null");
			serializer = s;
			this.classType = c;
			this.objectTarget = objectTarget;
			this.fieldTarget = fieldTarget;
		}

	}

	private static class WriteWorklistItem {
		Object obj;
		NonRecursiveSerializer<?> serializer;
		int fieldIndex;

		public WriteWorklistItem(NonRecursiveSerializer<?> s, Object obj) {
			serializer = s;
			this.obj = obj;
		}

	}

	@Override
	public void write(Kryo ckryo, Output output, T o) {
		// Try to reuse the work list
		FastStack<WriteWorklistItem> worklist = (FastStack<WriteWorklistItem>) this.cachedWorklist;
		if (!worklist.isEmpty())
			worklist = new FastStack<>(INITIAL_WORKLIST_SIZE_WRITE);

		PatchedKryo kryo = (PatchedKryo) ckryo;
		worklist.push(new WriteWorklistItem(this, o));
		Worklist: while (!worklist.isEmpty()) {
			WriteWorklistItem c = worklist.peek();

			CachedField[] fields = c.serializer.getFields();
			final Object object = c.obj;
			final int n = fields.length;
			for (int i = c.fieldIndex; i < n; i++) {
				CachedField f = fields[i];
				if (f instanceof ReflectField) {
					ReflectField field = (ReflectField) f;

					Object value;
					try {
						value = field.get(object);
					} catch (IllegalAccessException e) {
						throw new KryoException(e);
					}

					Class concreteType = field.resolveFieldClass();
					Serializer serializer = field.serializer;
					boolean canBeNull = field.canBeNull;
					if (concreteType == null) {
						// The concrete type of the field is unknown, write the class first.
						if (value == null) {
							kryo.writeClass(output, null);
							continue;
						}
						Registration registration = kryo.writeClass(output, value.getClass());
						if (serializer == null)
							serializer = registration.getSerializer();
						canBeNull = false;
					} else {
						if (serializer == null) {
							serializer = kryo.getSerializer(concreteType);
							// The concrete type of the field is known, always use the same serializer.
							if (field.valueClass != null && field.reuseSerializer)
								field.serializer = serializer;
						}
					}
					if (kryo.writeReferenceOrNull(output, value, canBeNull))
						continue;

					if (serializer instanceof NonRecursiveSerializer) {
						// YES!!
						// Add to worklist
						if (i + 1 < n) {
							c.fieldIndex = i + 1;
							worklist.push(new WriteWorklistItem((NonRecursiveSerializer<?>) serializer, value));
						} else {
							// optimization: end of list anyway
							c.fieldIndex = 0;
							c.serializer = (NonRecursiveSerializer<?>) serializer;
							c.obj = value;
						}
						continue Worklist;
					} else {
						// :( Recursion incoming
						serializer.write(kryo, output, value);
					}

				} else
					// These are "simple"
					f.write(output, object);
			}
			WriteWorklistItem popped = worklist.pop();
			if (popped != c)
				throw new AssertionError(
						"Workist entry after pop is different from the current one - this should not happen.");

		}
	}

}
