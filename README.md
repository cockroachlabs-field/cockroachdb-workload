# CockroachDB Workload

Simple tool with an interactive shell for running different workloads 
using the Java stack against CockroachDB.

Available workloads:

- **ledger** - financial ledger using double-entry principle
- **outbox** - inserts events simulating a transactional outbox
- **orders** - inserts and reads purchase orders

Each workload has an `init` command to setup the workload fixture and
one or more `run` commands to execute the actual workload.

## Disclaimer

This project is not officially supported by Cockroach Labs. Use of this library and the JDBC driver is entirely
at your own risk and Cockroach Labs makes no guarantees or warranties about its operation.

See [MIT](LICENSE.txt) license for terms and conditions.

## Project Setup

The project is packaged as a single executable JAR file and runs on any platform for which there is a
Java 17+ runtime.

### Prerequisites

- Java 17
    - https://openjdk.org/projects/jdk/17/
    - https://www.oracle.com/java/technologies/downloads/#java17
- Maven 3+ (optional, embedded wrapper available)
    - https://maven.apache.org/

Install the JDK (Ubuntu example):

    sudo apt-get install openjdk-17-jdk

Confirm the installation by running:

    java --version

### Clone the project

    git clone git@github.com:cockroachlabs-field/cockroachdb-workload.git
    cd cockroachdb-workload

### Build the executable jar

    chmod +x mvnw
    ./mvnw clean install

## Usage

Create the target database:

    cockroach sql --url postgresql://localhost:26257?sslmode=disable -e "CREATE database workload"

Start the shell with:

    java -jar target/workload.jar --help

or just:

    target/workload.jar --help

Type `help` for additional CLI guidance.

### Configuration

All parameters in `application-*.yaml` files can be overridden via CLI.
See [Common Application Properties](http://docs.spring.io/spring-boot/docs/current/reference/html/common-application-properties.html)
for more details.

# Terms of Use

This tool is an experimental prototype and not supported by Cockroach Labs. Use of this driver
is entirely at your own risk and Cockroach Labs makes no guarantees or warranties about its operation.

See [MIT](LICENSE.txt) for terms and conditions.

