fun main(args: Array<String>) {
    Array<caret>
}

// INVOCATION_COUNT: 2
// WITH_ORDER
// EXIST: { lookupString:"Array", tailText:"<T> (kotlin)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST_JAVA_ONLY: { lookupString:"Array", tailText:" (java.sql)" }

// EXIST: { lookupString:"ArrayList", tailText:"<E> (kotlin.collections)", icon: "org/jetbrains/kotlin/idea/icons/typeAlias.svg"}
// EXIST_JAVA_ONLY: { lookupString:"ArrayList", tailText:"<E> (java.util)"}





// Developer! Every ancient failure of this test is important!