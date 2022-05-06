package pkg;

public class TestGenericsHierarchy<T extends Number> {
  public T field;

  public <V extends T> void test(V v) {
    this.field = v;
    setField(v);
  }

  public void setField(T field) {
    this.field = field;
  }
}
