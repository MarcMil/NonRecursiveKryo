package test;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.PatchedKryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.*;

public class HelloKryo {
   static public void main (String[] args) throws Exception {
	  Kryo kryo = new PatchedKryo();
	   //Use original for a nice stack overflow
      //Kryo kryo = new Kryo();
      kryo.register(SomeClass.class);
      kryo.setReferences(true);

      SomeClass object = new SomeClass();
      SomeClass n = object;
      //create a really deep nested list
      for (int i = 0; i < 100000; i++) {
    	  n.next = new SomeClass();
    	  n = n.next;
      }
      n.value = "Hello Kryo!";

      Output output = new Output(new FileOutputStream("file.bin"));
      kryo.writeObject(output, object);
      output.close();

      Input input = new Input(new FileInputStream("file.bin"));
      SomeClass object2 = kryo.readObject(input, SomeClass.class);
      int next = 0;
      while (object2.next != null) {
    	  object2 = object2.next;
    	  next++;
      }
      System.out.println("Nested depth: " + next + ", " + n.value);
      
      input.close();   
   }
   
   static public class SomeClass {
      SomeClass next;
      Object value;
   }
}