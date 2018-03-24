**graphql-gateway** - SDL-based GraphQL gateway for REST and GraphQL-based micro-services

[![Build Status](https://travis-ci.org/OlegIlyenko/graphql-gateway.svg?branch=master)](https://travis-ci.org/OlegIlyenko/graphql-gateway)

Still at early WIP stage.

### Setup

*Prerequisites* 

* [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* [SBT](http://www.scala-sbt.org/download.html)

*Running*

After starting the server with

```bash
sbt run

# or, if you want to watch the source code changes
 
sbt ~reStart
``` 

you can run queries interactively using [GraphiQL](https://github.com/graphql/graphiql) by opening [http://localhost:8080](http://localhost:8080) in a browser or query the `/graphql` endpoint directly.

## License

**graphql-gateway** is licensed under [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
