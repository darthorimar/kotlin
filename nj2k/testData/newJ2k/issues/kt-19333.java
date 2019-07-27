public class TestCharAsInt {
    public static void foo(char ch) {
        bar('a');
        bar(ch);
    }

    public static void bar(int i) {}
}