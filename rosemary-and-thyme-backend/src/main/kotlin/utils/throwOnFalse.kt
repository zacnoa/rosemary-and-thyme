package utils

import kotlin.jvm.Throws

inline fun <T: Throwable> throwOnFalse(predicate:Boolean, exception:(String)->T, message:()->String) {

    if(!predicate) throw exception(message())
}