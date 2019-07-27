public class C {
    public void f() {
        String[] displayMapping = {"NUL", "ONE"/*, ...*/};
        char chr = '\0';
        String toDisplay = displayMapping[chr];
        System.out.println(toDisplay);
    }
}