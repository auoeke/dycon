package net.auoeke.dycon;

import java.util.function.Supplier;

public class Dycon {
	public static <T> T ldc(Supplier<T> initializer) {
		throw new RuntimeException("Dycon::ldc may not be called at runtime");
	}
}
