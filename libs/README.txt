Put the following jars here, taken from YOUR modpack so the versions match exactly:

  origins-<version>.jar
  apoli-<version>.jar
  calio-<version>.jar

Loom will remap them automatically for the development environment.
If you would rather resolve them from Modrinth's maven, comment out the
`modImplementation fileTree(...)` line in build.gradle and uncomment the
three `maven.modrinth:` lines below it.
