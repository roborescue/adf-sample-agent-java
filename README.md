# `adf-sample-agent-java` Agent Development Framework Sample Agent Java

(Linux) Instructions to download, build and run the sample implementation using the Agent Development Framework (ADF)

## 1. Software Pre-Requisites

* Git
* OpenJDK Java 17
* Gradle

## 2. Download

```bash
$ git clone https://github.com/roborescue/adf-sample-agent-java.git
```

## 3. Compile

```bash

$ cd adf-sample-agent-java

$ ./gradlew clean

$ ./gradlew build
```

## 4. Execute

The `adf-sample-agent-java` is a sample team implementation for the RCRS (`rcrs-server`) using the ADF core (`adf-core-java`).

To run the `adf-sample-agent-java`, first the `rcrs-server` must be running (Instructions of how to download, compile and run the `rcrs-server` are available at <https://github.com/roborescue/rcrs-server>).

After start the `rcrs-server`, open a new terminal window and execute

```bash

$ cd adf-sample-agent-java

$ ./launch.sh -all
```

## 5. Support

To report a bug, suggest improvements or request support, please open an issue at GitHub <https://github.com/roborescue/rcrs-adf-sample/issues>.