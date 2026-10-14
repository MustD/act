# Stack traces in Play vitals stay readable: the mapping file ships in the AAB, these keep the line numbers in it.
-keepattributes SourceFile,LineNumberTable

# Rules are added only where a minified release build is seen to need them. Everything in the dependency tree that
# reflects (kotlinx-serialization, Ktor, Koin, Coil) ships its own consumer rules.
