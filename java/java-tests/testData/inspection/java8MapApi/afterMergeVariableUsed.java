// "Replace with 'merge' method call" "true"
import java.util.Map;

public class Main {
  public void testMerge(Map<String, Integer> map, String key, int add) {
    Integer val = map.get(key);
      map.merge(key, add, (a, b) -> b + a)
    System.out.println(val);
  }
}