// ERROR: Type mismatch: inferred type is Collector<CharSequence!, *, String!> but Collector<in Any!, TypeVariable(A)!, TypeVariable(R)!>! was expected
import java.util.stream.Collectors

internal class JavaCode {
    fun toJSON(collection: Collection<Int>): String {
        return "[" + collection.stream().map<Any> { obj: Int -> obj.toString() }.collect(Collectors.joining(", ")).toString() + "]"
    }
}
