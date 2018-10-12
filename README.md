# `rcrs-adf-sample` RCRS Agent Development Framework (Sample)

(Linux) Instructions to download, build and run the sample implementation using the Agent Development Framework (ADF)

## 1. Software Pre-Requisites

- Git
- OpenJDK Java 8+

## 2. Download

```bash

$ git clone https://github.com/roborescue/rcrs-adf-sample.git
```

## 3. Compile

```bash

$ ./clean

$ ./compile.sh
```

## 4. Execute

The `rcrs-adf-sample` is a sample team implementation for the RCRS (`rcrs-server`) using the ADF core (`rcrs-adf-core`).

To run the `rcrs-adf-sample`, first the `rcrs-server` must be running (Instructions of how to download, compile and run the `rcrs-server` are available at <https://github.com/roborescue/rcrs-server>).

After start the `rcrs-server`, open a new terminal window and execute

```bash

$ cd rcrs-adf-sample

$ ./launch.sh -all
```

## 5. Support

To report a bug, suggest improvements or request support, please open an issue at GitHub <https://github.com/roborescue/rcrs-adf-sample/issues>.