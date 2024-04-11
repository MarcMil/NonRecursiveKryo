package com.esotericsoftware.kryo;

import java.lang.reflect.Field;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.NonRecursiveSerializer;
import com.esotericsoftware.kryo.util.IntArray;

/**
 * We need that so we can access some package-visible methods and fields
 * The current implementation may easily break on newer versions of Kryo!
 * Test it first.
 */
public class PatchedKryo extends Kryo {
	public static final int REF = -1;
	public static final int NO_REF = -2;
	private static Field readObjectField, referenceIDField;
	static {
		try {
			//If these fields were protected, we could use them, but no chance this way.
			referenceIDField = Kryo.class.getDeclaredField("readReferenceIds");
			referenceIDField.setAccessible(true);
			readObjectField = Kryo.class.getDeclaredField("readObject");
			readObjectField.setAccessible(true);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private IntArray readReferenceIds;

	public PatchedKryo() {
		super();
		init();
	}
	
	public PatchedKryo(ReferenceResolver referenceResolver) {
		super(referenceResolver);
		init();
	}

	public PatchedKryo(ClassResolver classResolver, ReferenceResolver referenceResolver) {
		super(classResolver, referenceResolver);
		init();
	}

	private void init() {
		setOptimizedGenerics(false);
		setDefaultSerializer(new SerializerFactory<Serializer>() {

			@Override
			public Serializer newSerializer(Kryo kryo, Class type) {
				return new NonRecursiveSerializer<>(kryo, type);
			}

			@Override
			public boolean isSupported(Class type) {
				return true;
			}
			
		});
		try {
			readReferenceIds = (IntArray) referenceIDField.get(this);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean writeReferenceOrNull(Output output, Object object, boolean mayBeNull) {
		return super.writeReferenceOrNull(output, object, mayBeNull);
	}

	@Override
	public int readReferenceOrNull(Input input, Class type, boolean mayBeNull) {
		return super.readReferenceOrNull(input, type, mayBeNull);
	}

	public Object getReadObject() {
		try {
			return readObjectField.get(this);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public IntArray getReadReferenceIds() {
		return readReferenceIds;
	}
	


}
