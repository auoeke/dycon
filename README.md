Dycon is a group of 2 interdependent projects:
- dycon (for Java 11+): a single class `Dycon` containing a method that can be invoked to trigger dycon-javac
- dycon-javac (for Java 17+): a plugin for the standard Java compiler that replaces calls to `Dycon::ldc` by `ldc` instructions.

Consider the example below.
```java
private static final Thing expensiveObject = expensiveInitialization();

public static Stuff doStuff() {
	return expensiveObject.getStuff();
}
```
Since `expensiveObject` takes a while to initialize, is not always necessary and might be initialized as a side effect
of access to the implicit class for another reason, its initialization can waste much time. It might also cause a class
loading circle. One can work around these problems by delaying its initialization until it's necessary.
```java
private static Thing expensiveObject;

private static Thing expensiveObject() {
	return expensiveObject == null ? expensiveObject = expensiveInitialization() : expensiveObject;
}
```

Dycon exploits the constant pool form `CONSTANT_Dynamic` introduced to Java 11 by
[JEP 309](https://openjdk.org/jeps/309) in order to provide an alternative solution to these problems.
[JEP 303](https://openjdk.org/jeps/303) is a candidate that proposes intrinsics for `ldc` and `invokedynamic`. This
project provides a simpler interface for `ldc` intrinsics for arbitrary dynamic constants.
```java
import static net.auoeke.dycon.Dycon.ldc;
...
private static Thing expensiveObject() {
	return ldc(This::expensiveInitialization);
	// or return ldc(() -> expensiveInitialization());
}
```
dycon-javac finds this `ldc` invocation and extracts a handle to the target of the method reference or lambda and
replaces the call to `ldc` and the generation of the `Supplier` object by an `ldc` instruction with an index to a
`CONSTANT_Dynamic_info` constant pool entry that points to a bootstrap method that invokes the method handle. After the first
call the JVM will reuse its result instead of invoking it again.

```diff
- invkedynamic LambdaMetafactory::metafactory(MethodHandles.Lookup, String, MethodType, MethodType, MethodHandle, MethodType)CallSite(()Object, This::expensiveInitialization | This::lambda$expensiveObject$0, ()Thing)
- invokestatic Dycon::ldc(Supplier)Object
+ ldc ConstantBootstraps::invoke(MethodHandles.Lookup, String, Class, MethodHandle, Object...)Object(This::expensiveInitialization | This::lambda$expensiveObject$0)
```

## concurrent initialization

The JVM may invoke bootstrap methods concurrently so manual synchronization is necessary.

## download

[dycon](https://repo1.maven.org/maven2/net/auoeke/dycon/) and [dycon-javac](https://repo1.maven.org/maven2/net/auoeke/dycon-javac/) are available from Central.
```groovy
dependencies {
	annotationProcessor("net.auoeke:dycon-javac:latest.release")
	compileOnly("net.auoeke:dycon:latest.release")
}
```

## miscellanea

```java
static int count;

static Unsafe lazyUnsafe() {
    return Dycon.ldc(() -> {
        ++count;
        return (Unsafe) MethodHandles.privateLookupIn(Unsafe.class, MethodHandles.lookup())
            .findStaticGetter(Unsafe.class, "theUnsafe", Unsafe.class)
            .invokeExact();
    });
}

@Test void test() {
    assert count == 0 : count;
    assert lazyUnsafe() != null;
    assert count == 1 : count;
    assert lazyUnsafe() == lazyUnsafe();
    assert count == 1 : count;
}
```

"Dycon" is derived from "condy" (common abbreviation of `CONSTANT_Dynamic`).
