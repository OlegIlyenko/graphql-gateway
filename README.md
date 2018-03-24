**graphql-gateway** - SDL-based GraphQL gateway for REST and GraphQL-based micro-services

[![Build Status](https://travis-ci.org/OlegIlyenko/graphql-gateway.svg?branch=master)](https://travis-ci.org/OlegIlyenko/graphql-gateway)

Project is still at very early WIP stage.

Schema definition is based on [GraphQL SDL](https://github.com/facebook/graphql/pull/90). SDL syntax allows you to define full GraphQL 
schema with interfaces, types, enums etc. In order to provide resolution logic for the fields, you can use directives described below. 
Directives will define how fields will behave. By default (if no directive is provided), field resolve function will treat a contextual 
value as a JSON object and will return it's property with the same name. (check out an [example schema](https://github.com/OlegIlyenko/graphql-gateway/blob/master/testSchema.graphql))

### Supported SDL Directives

```graphql
directive @httpGet(url: String!, headers: ObjectOrList, query: ObjectOrList, forAll: String) on FIELD_DEFINITION
```

Provides a way to resolve the field with a result of a GET HTTP request.  
  
Supports following arguments:

* `url` - the URL of an HTTP request
* `headers` - headers that should be sent with the request. The value can be either an input object (e.g `{Authorization: "Bearer FOOBARBAZ"}`) or a list with name-value pairs (e.g. `[{name: "Authorization", value: "Bearer FOOBARBAZ"}]`)
* `query` - query string parameters that should be sent with the request. The value can be either an input object (e.g `{limit: 10, offset: 0}`) or a list with name-value pairs (e.g. `[{name: "page-number", value: "1"}]`)
* `forAll` - A [JSON Path](http://goessner.net/articles/JsonPath/) expression. For every element, returned by this expression executed against current context value, a separate HTTP request would be sent. An `elem` placeholder scope may be used in combination with this argument.

`url`, `headers` and `query` may contain the placeholders which are described below. `value` directive may be used in combination with `httpGet` - it will extract part of the relevant JSON out of the HTTP response.

```graphql
directive @includeGraphQL(schemas: [GraphQLSchemaInclude!]!) on SCHEMA

input GraphQLSchemaInclude {
  name: String!
  url: String!
} 
```

Includes external GraphQL schemas (based on GraphQL endpoint URL)

```graphql
directive @include(fields: [GraphQLIncludeFields!]!) on OBJECT

input GraphQLIncludeFields {
  "the name of the schema included with @includeGraphQL"
  schema: String!
  
  "name of the type from the extenal schema" 
  type: String!
  
  "optional list of fields to include (of not provided, all fields are included)"    
  fields: [String!]
} 
```

Adds fields loaded from the external GraphQL schema

```graphql
directive @const(value: Any!) on FIELD_DEFINITION | SCHEMA
```
            
Provides a way to resolve a field with a constant value. `value` can be any valid GraphQL input value. It would be treated as a JSON value.

```graphql
directive @jsonConst(value: String!) on FIELD_DEFINITION | SCHEMA
```
            
Provides a way to resolve a field with a constant value. `value` should be a valid JSON value.

```graphql
directive @arg(name: String!) on FIELD_DEFINITION
```
            
Provides a way to resolve a field with value of one of its arguments.

```graphql
directive @value(name: String, path: String) on FIELD_DEFINITION
```
            
Extracts a value(s) from the context object. It supports following extractors via arguments (only one can be used):

* `name` - Extracts a named property value from a context JSON object
* `path` - A [JSON Path](http://goessner.net/articles/JsonPath/) expression. It would be executed against current context JSON value.

```graphql
directive @context(name: String, path: String) on FIELD_DEFINITION
```
            
Extracts a value(s) from the context object defined on the schema level. It supports following extractors via arguments (only one can be used):

* `name` - Extracts a named property value from a JSON object
* `path` - A [JSON Path](http://goessner.net/articles/JsonPath/) expression. It would be executed against current context JSON value, which is defined at the schema level.

### Placeholders

Placeholders may be used in some the directive arguments (inside of the strings) and the syntax looks like this:

```
${value.$.results[0].film}
```
            
The placeholder consists of two parts separated by dot (`.`): the scope (`value` in this case) and the extractor (`$.results[0].film` - a JSON Path extractor in this example). The scope defines a place/value from which you would like extract a value. Following scopes are supported:

* `arg` - field argument
* `value` - a context value
* `ctx` - a context value which is defined on a schema level
* `elem` - an extracted element that comes from the `forAll` argument

The extractor can be either a string (the name of the property) or a [JSON Path](http://goessner.net/articles/JsonPath/) expression.

### Setup

**Prerequisites** 

* [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* [SBT](http://www.scala-sbt.org/download.html)

**Running**

After starting the server with

```bash
sbt run

# or, if you want to watch the source code changes
 
sbt ~reStart
``` 

you can run queries interactively using [GraphiQL](https://github.com/graphql/graphiql) by opening [http://localhost:8080](http://localhost:8080) in a browser or query the `/graphql` endpoint directly.

## License

**graphql-gateway** is licensed under [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
