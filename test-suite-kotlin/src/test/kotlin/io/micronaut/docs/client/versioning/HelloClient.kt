package io.micronaut.docs.client.versioning

// tag::imports[]
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.reactivex.Single
// end::imports[]

// tag::clazz[]
@Client("/hello")
@Version("1") // <1>
interface HelloClient {

    @Get("/greeting/{name}")
    fun sayHello(name : String) : String

    @Version("2")
    @Get("/greeting/{name}")
    fun sayHelloTwo(name : String) : Single<String>  // <2>
}
// end::clazz[]
