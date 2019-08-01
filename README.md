# Okra

Okra implementation using synchronous MongoDB Java Driver.
This is the fastest synchronous Okra implementation ever made.

[![Build Status][travis-badge]][travis-url] [![Code Coverage][codecov-badge]][codecov-url] [![Artifacts][jitpack-badge]][jitpack-url]

## Requirements

* Java 8
* MongoDB Synchronous Driver

## Note 

Pull Requests are always welcome! We will always review and accept them really fast.

## Getting Started

See how to get started in the project wiki
https://github.com/OkraScheduler/OkraSync/wiki/Getting-Started

## Dependency

### Gradle

build.gradle

```groovy
    allprojects {
        repositories {
            maven { url "https://jitpack.io" }
        }
    }
```

```groovy
    dependencies {
        compile 'com.github.greenmilellc-org:OkraSync:x.y.z'
    }
```

#### Maven

```xml
    <dependency>
        <groupId>com.github.greenmilellc-org</groupId>
        <artifactId>OkraSync</artifactId>
        <version>x.y.z</version>
    </dependency>

    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>
```

[codecov-badge]: https://codecov.io/gh/OkraScheduler/OkraSync/branch/master/graph/badge.svg
[codecov-url]: https://codecov.io/gh/OkraScheduler/OkraSync
[travis-badge]: https://travis-ci.org/OkraScheduler/OkraSync.svg?branch=master
[travis-url]: https://travis-ci.org/OkraScheduler/OkraSync
[jitpack-badge]: https://jitpack.io/v/OkraScheduler/OkraSync.svg
[jitpack-url]: https://jitpack.io/#OkraScheduler/OkraSync
