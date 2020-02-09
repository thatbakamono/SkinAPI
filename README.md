# SkinAPI

## Gradle:

```gradle
repositories {
	maven { url "https://jitpack.io" }
}

dependencies {
	compile "com.github.KernelErr0r:SkinAPI:v1.0.1"
}
```

## Maven:

```xml
<repositories>
	<repository>
		<id>jitpack</id>
		<url>https://jitpack.io</url>
	</repository>
</repositories>

<dependencies>
	<dependency>
		<groupId>com.github.KernelErr0r</groupId>
		<artifactId>SkinAPI</artifactId>
		<version>v1.0.1</version>
	</dependency>
</dependencies>
```

## Basic example

```java
@Override
public void onEnable() {
	SkinAPI skinAPI = new SkinAPI(this);

	skinAPI.getSkin(Bukkit.getPlayer("test1"), id -> {
		skinAPI.setSkin(Bukkit.getPlayer("test2"), id, () -> {
			Bukkit.broadcastMessage("Done!");
		});
	})
}
```