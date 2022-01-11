[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.feature.extension.unpack.svg)](https://www.javadoc.io/doc/org.apache.sling/org.apache.sling.feature.extension.unpack)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.feature.extension.unpack/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.feature.extension.unpack%22)&#32;[![feature](https://sling.apache.org/badges/group-feature.svg)](https://github.com/apache/sling-aggregator/blob/master/docs/groups/feature.md) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

The jar can be used in the following ways:

## Feature model launcher extension

When added to the classpath of the feature launcher it will unzip extensions according to a framework property (see below).

## OSGi installer extension

When installed as a bundle into sling, it will act as a extension to the OSGi installer and handle zip files that get installed as well as extensions in feature models that get installed (both, again, according to a framework property).


## Converter

When invoked on the commandline, there is a converter main class that can be used to wrap a list of urls into a feature with an extension.

It looks like this:

```
java -cp ${HOME}/.m2/repository/org/apache/sling/org.apache.sling.commons.johnzon/1.2.2/org.apache.sling.commons.johnzon-1.2.2.jar:${HOME}/.m2/repository/org/apache/sling/org.apache.sling.feature/1.2.6/org.apache.sling.feature-1.2.6.jar:target/org.apache.sling.feature.extension.unpack-0.1.0-SNAPSHOT.jar \
 org.apache.sling.feature.extension.unpack.impl.converter.Converter \
 <mvn-id-of-resulting-feature> \
 <name-of-extension-in-feature> \
 <path-to-resulting-feature-file> \
 <path-to-mvn-repository-to-store-artifacts> \
 key=<optional-key-for-manifest> \
 value=<optional-valure-for-manifest>\
 <space separated list of urls>

```

Alternatively, there is an assembly provided.

## Configuration

The framework property ```org.apache.sling.feature.unpack.extensions``` can be used to give an OSGi header clause configuring which extensions to unzip, to which dir, and with what required manifest header.

It looks like this:

```-Dorg.apache.sling.feature.unpack.extensions=<extension-name>;dir:=<path-to-dir-on-disc>;default:=<[true|false]>;key:=<optional-key-in-manifest>;value:=<optional-value-for-key-in-manifest>;index:=<path-header-in-manifest><[,<clause>]*>```





