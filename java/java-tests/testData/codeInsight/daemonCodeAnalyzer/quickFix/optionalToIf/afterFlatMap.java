// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  private void reusesVariable(String in) {
      if (in == null) throw new NullPointerException();
      String id = id(in);
      if (id == null) throw new NoSuchElementException("No value present");
      Object out = id;
  }

  private void checkIsRemoved(String in) {
      if (in == null) throw new NullPointerException();
      String out = in;
  }

  void simple(String in) {
      String out = "bar";
      if (in != null) out = in;
  }

  void simpleWithMap(String in) {
      String out = "bar";
      if (in != null) {
          String id = id(in);
          if (id != null) out = id;
      }
  }

  void nested(String in) {
      String out = "bar";
      if (in != null) out = in;
  }

  void outer(String in, String p) {
      String out = "bar";
      if (in != null) {
          String value = in + in + p;
          out = value;
      }
  }

  void nullableOuter(String in, String p) {
      String out = "bar";
      if (in != null) {
          if (p == null) throw new NullPointerException();
          out = p;
      }
  }

  void nestedFlatMap(String var0) {
      boolean b = false;
      if (var0 != null) {
          String var2 = var0.toLowerCase();
          b = true;
      }
  }

  void flatMapsWithSameParamName(String param0) {
      if (param0 == null) throw new NullPointerException();
      String s = "foo";
      String toLowerCase = ("foo").toLowerCase();
      String bar = "bar";
  }

  String flatMapWithOrInside() {
      Object o1 = null;
      String empty = null;
      throw new NoSuchElementException("No value present");
  }

  <T> T id(T t) {
    return t;
  }
}