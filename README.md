**graphql-gateway** - SDL-based GraphQL gateway for REST and GraphQL-based micro-services

[![Build Status](https://travis-ci.org/OlegIlyenko/graphql-gateway.svg?branch=master)](https://travis-ci.org/OlegIlyenko/graphql-gateway)

⚠️ **Project is a POC and at very early experimental/WIP stage!** ⚠️

Schema definition is based on [GraphQL SDL](https://github.com/facebook/graphql/pull/90). SDL syntax allows you to define full GraphQL 
schema with interfaces, types, enums etc. In order to provide resolution logic for the fields, you can use directives described below. 
Directives will define how fields will behave. By default (if no directive is provided), field resolve function will treat a contextual 
value as a JSON object and will return its property with the same name. (check out an [example schema](https://github.com/OlegIlyenko/graphql-gateway/blob/master/testSchema.graphql))

### Supported SDL Directives

```graphql
directive @httpGet(url: String!, headers: [Header!], query: [QueryParam!], forAll: String) on FIELD_DEFINITION

input Header {
  name: String!
  value: String!
}

input QueryParam {
  name: String!
  value: String!
}
```

Provides a way to resolve the field with a result of a GET HTTP request.  
  
Supports following arguments:

* `url` - the URL of an HTTP request
* `headers` - headers that should be sent with the request (e.g. `[{name: "Authorization", value: "Bearer FOOBARBAZ"}]`)
* `query` - query string parameters that should be sent with the request (e.g. `[{name: "page-number", value: "1"}]`)
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
directive @fake(expr: String, min: Int, max: Int, past: Boolean, future: Boolean) on FIELD_DEFINITION 
```

Provides fake data. If the field returns a list, it's size can be customized with `min`/`max`. 
If the field returns a `DateTime`, then `past`/`future` would be used.    

The fake data type can be customized with an expression `expr`. An expression has following structure:

```
#{category.name 'arg1','arg2'}
```

Here is a small example: 

```graphql
type Info {
  createAt: DateTime
}

type Query {
  name: String! @fake(expr: "#{name.fullName}")
  address: String! @fake(expr: "#{address.fullAddress} - #{numerify 'SOME######'}")
  address: Info! @fake
}
``` 

<details>
  <summary>Full list of all available faker expressions</summary>
  
* `bothify(string: String)`

  Applies both a numerify(String) and a letterify(String) over the incoming string.
* `bothify(string: String, isUpper: Boolean)`

  Applies both a numerify(String) and a letterify(String) over the incoming string.
* `letterify(letterString: String, isUpper: Boolean)`

  Returns a string with the '?' characters in the parameter replaced with random alphabetic characters. For example, the string "12??34" could be replaced with a string like "12AB34".
* `letterify(letterString: String)`

  Returns a string with the '?' characters in the parameter replaced with random alphabetic characters. For example, the string "12??34" could be replaced with a string like "12AB34".
* `numerify(numberString: String)`

  Returns a string with the '#' characters in the parameter replaced with random digits between 0-9 inclusive. For example, the string "ABC##EFG" could be replaced with a string like "ABC99EFG".
* `regexify(regex: String)`

  Generates a String that matches the given regular expression.
* `resolve(key: String)`
* `address`
  * `address.buildingNumber`
  * `address.city`
  * `address.cityName`
  * `address.cityPrefix`
  * `address.citySuffix`
  * `address.country`
  * `address.countryCode`
  * `address.firstName`
  * `address.fullAddress`
  * `address.lastName`
  * `address.latitude`
  * `address.longitude`
  * `address.secondaryAddress`
  * `address.state`
  * `address.stateAbbr`
  * `address.streetAddress(includeSecondary: Boolean)`
  * `address.streetAddress`
  * `address.streetAddressNumber`
  * `address.streetName`
  * `address.streetPrefix`
  * `address.streetSuffix`
  * `address.timeZone`
  * `address.zipCode`
  * `address.zipCodeByState(stateAbbr: String)`
* `ancient`
  * `ancient.god`
  * `ancient.hero`
  * `ancient.primordial`
  * `ancient.titan`
* `app`
  * `app.author`
  * `app.name`
  * `app.version`
* `artist`
  * `artist.name`
* `beer`
  * `beer.hop`
  * `beer.malt`
  * `beer.name`
  * `beer.style`
  * `beer.yeast`
* `book`
  * `book.author`
  * `book.genre`
  * `book.publisher`
  * `book.title`
* `bool`
  * `bool.bool`
* `business`
  * `business.creditCardExpiry`
  * `business.creditCardNumber`
  * `business.creditCardType`
* `cat`
  * `cat.breed`
  * `cat.name`
  * `cat.registry`
* `chuckNorris`
  * `chuckNorris.fact`
* `code`
  * `code.asin`
  * `code.ean13`
  * `code.ean8`
  * `code.gtin13`
  * `code.gtin8`
  * `code.imei`
  * `code.isbn10`
  * `code.isbn10(separator: Boolean)`
  * `code.isbn13`
  * `code.isbn13(separator: Boolean)`
  * `code.isbnGroup`

    This can be overridden by specifying code: isbn_group: "some expression" in the appropriate yml file.
  * `code.isbnGs1`

    This can be overridden by specifying code: isbn_gs1: "some expression" in the appropriate yml file.
  * `code.isbnRegistrant`

    This can be overridden by specifying code: isbn_registrant: "some expression" in the appropriate yml file.
* `color`
  * `color.name`
* `commerce`
  * `commerce.color`
  * `commerce.department`
  * `commerce.material`
  * `commerce.price(min: Double, max: Double)`
  * `commerce.price`

    Generate a random price between 0.00 and 100.00
  * `commerce.productName`
  * `commerce.promotionCode`
  * `commerce.promotionCode(digits: Int)`
* `company`
  * `company.bs`

    When a straight answer won't do, BS to the rescue!
  * `company.buzzword`
  * `company.catchPhrase`

    Generate a buzzword-laden catch phrase.
  * `company.industry`
  * `company.logo`

    Generate a random company logo url in PNG format.
  * `company.name`
  * `company.profession`
  * `company.suffix`
  * `company.url`
* `crypto`
  * `crypto.md5`
  * `crypto.sha1`
  * `crypto.sha256`
  * `crypto.sha512`
* `date`
* `demographic`
  * `demographic.demonym`
  * `demographic.educationalAttainment`
  * `demographic.maritalStatus`
  * `demographic.race`
  * `demographic.sex`
* `educator`
  * `educator.campus`
  * `educator.course`
  * `educator.secondarySchool`
  * `educator.university`
* `esports`
  * `esports.event`
  * `esports.game`
  * `esports.league`
  * `esports.player`
  * `esports.team`
* `file`
  * `file.extension`
  * `file.fileName`
  * `file.fileName(dirOrNull: String, nameOrNull: String, extensionOrNull: String, separatorOrNull: String)`
  * `file.mimeType`
* `finance`
  * `finance.bic`

    Generates a random Business Identifier Code
  * `finance.creditCard`
  * `finance.iban`
  * `finance.iban(countryCode: String)`
* `food`
  * `food.ingredient`
  * `food.measurement`
  * `food.spice`
* `friends`
  * `friends.character`
  * `friends.location`
  * `friends.quote`
* `gameOfThrones`
  * `gameOfThrones.character`
  * `gameOfThrones.city`
  * `gameOfThrones.dragon`
  * `gameOfThrones.house`
  * `gameOfThrones.quote`
* `hacker`
  * `hacker.abbreviation`
  * `hacker.adjective`
  * `hacker.ingverb`
  * `hacker.noun`
  * `hacker.verb`
* `harryPotter`
  * `harryPotter.book`
  * `harryPotter.character`
  * `harryPotter.location`
  * `harryPotter.quote`
* `hipster`
  * `hipster.word`
* `idNumber`
  * `idNumber.invalid`
  * `idNumber.invalidSvSeSsn`

    Specified as #{IDNumber.invalid_sv_se_ssn} in sv-SE.yml
  * `idNumber.ssnValid`
  * `idNumber.valid`
  * `idNumber.validSvSeSsn`

    Specified as #{IDNumber.valid_sv_se_ssn} in sv-SE.yml
* `internet`
  * `internet.avatar`

    Generates a random avatar url based on a collection of profile pictures of real people. All this avatar have been authorized by its awesome users to be used on live websites (not just mockups). For more information, please visit: http://uifaces.com/authorized
  * `internet.domainName`
  * `internet.domainSuffix`
  * `internet.domainWord`
  * `internet.emailAddress`
  * `internet.emailAddress(localPart: String)`
  * `internet.image(width: Int, height: Int, gray: java.lang.Boolean, text: String)`

    Same as image() but allows client code to choose a few image characteristics
  * `internet.image`

    Generates a random image url based on the lorempixel service. All the images provided by this service are released under the creative commons license (CC BY-SA). For more information, please visit: http://lorempixel.com/
  * `internet.ipV4Address`

    returns an IPv4 address in dot separated octets.
  * `internet.ipV4Cidr`
  * `internet.ipV6Address`

    Returns an IPv6 address in hh:hh:hh:hh:hh:hh:hh:hh format.
  * `internet.ipV6Cidr`
  * `internet.macAddress(prefix: String)`

    Returns a MAC address in the following format: 6-bytes in MM:MM:MM:SS:SS:SS format.
  * `internet.macAddress`
  * `internet.password(minimumLength: Int, maximumLength: Int)`
  * `internet.password`
  * `internet.password(minimumLength: Int, maximumLength: Int, includeUppercase: Boolean, includeSpecial: Boolean)`
  * `internet.password(minimumLength: Int, maximumLength: Int, includeUppercase: Boolean)`
  * `internet.privateIpV4Address`
  * `internet.publicIpV4Address`
  * `internet.safeEmailAddress`
  * `internet.safeEmailAddress(localPart: String)`
  * `internet.slug`
  * `internet.slug(wordsOrNull: [String], glueOrNull: String)`
  * `internet.url`
* `job`
  * `job.field`
  * `job.keySkills`
  * `job.position`
  * `job.seniority`
  * `job.title`
* `lordOfTheRings`
  * `lordOfTheRings.character`
  * `lordOfTheRings.location`
* `lorem`
  * `lorem.character(includeUppercase: Boolean)`
  * `lorem.character`
  * `lorem.characters(includeUppercase: Boolean)`
  * `lorem.characters`
  * `lorem.characters(minimumLength: Int, maximumLength: Int)`
  * `lorem.characters(minimumLength: Int, maximumLength: Int, includeUppercase: Boolean)`
  * `lorem.characters(includeUppercase: Boolean)`
  * `lorem.characters(minimumLength: Int, maximumLength: Int)`
  * `lorem.fixedString(numberOfLetters: Int)`

    Create a string with a fixed size. Can be useful for testing validator based on length string for example
  * `lorem.paragraph(sentenceCount: Int)`
  * `lorem.paragraph`
  * `lorem.sentence(wordCount: Int)`

    Create a sentence with a random number of words within the range (wordCount+1)..(wordCount+6).
  * `lorem.sentence`

    Create a sentence with a random number of words within the range 4..10.
  * `lorem.sentence(wordCount: Int, randomWordsToAdd: Int)`

    Create a sentence with a random number of words within the range (wordCount+1)..(wordCount+randomWordsToAdd). Set randomWordsToAdd to 0 to generate sentences with a fixed number of words.
  * `lorem.word`
* `matz`
  * `matz.quote`
* `music`
  * `music.chord`
  * `music.instrument`
  * `music.key`
* `name`
  * `name.firstName`

    Returns a random 'given' name such as Aaliyah, Aaron, Abagail or Abbey
  * `name.fullName`

    Returns the same value as name()
  * `name.lastName`

    Returns a random last name such as Smith, Jones or Baldwin
  * `name.name`

    A multipart name composed of an optional prefix, a firstname and a lastname or other possible variances based on locale. Examples: James Jones Jr. Julie Johnson
  * `name.nameWithMiddle`

    A multipart name composed of an optional prefix, a given and family name, another 'firstname' for the middle name and an optional suffix such as Jr. Examples: Mrs. Ella Geraldine Fitzgerald Jason Tom Sawyer Jr. Helen Jessica Troy
  * `name.prefix`

    Returns a name prefix such as Mr., Mrs., Ms., Miss, or Dr.
  * `name.suffix`

    Returns a name suffix such as Jr., Sr., I, II, III, IV, V, MD, DDS, PhD or DVM
  * `name.title`

    A three part title composed of a descriptor level and job. Some examples are : (template) {descriptor} {level} {job} Lead Solutions Specialist National Marketing Manager Central Response Liaison
  * `name.username`

    A lowercase username composed of the first_name and last_name joined with a '.'. Some examples are: (template) firstName().lastName() jim.jones jason.leigh tracy.jordan
* `number`
  * `number.digit`
  * `number.digits(count: Int)`
  * `number.numberBetween(min: Int, max: Int)`
  * `number.numberBetween(min: Int, max: Int)`
  * `number.randomDigit`

    Returns a random number from 0-9 (both inclusive)
  * `number.randomDigitNotZero`

    Returns a random number from 1-9 (both inclusive)
  * `number.randomDouble(maxNumberOfDecimals: Int, min: Int, max: Int)`
  * `number.randomDouble(maxNumberOfDecimals: Int, min: Int, max: Int)`
  * `number.randomNumber(numberOfDigits: Int, strict: Boolean)`
  * `number.randomNumber`

    Returns a random number
* `phoneNumber`
  * `phoneNumber.cellPhone`
  * `phoneNumber.phoneNumber`
* `pokemon`
  * `pokemon.location`
  * `pokemon.name`
* `random`
  * `random.nextDouble`
  * `random.nextInt(n: Int)`
  * `random.nextLong(n: long)`
  * `random.nextLong`
* `rickAndMorty`
  * `rickAndMorty.character`
  * `rickAndMorty.location`
  * `rickAndMorty.quote`
* `rockBand`
  * `rockBand.name`
* `shakespeare`
  * `shakespeare.asYouLikeItQuote`
  * `shakespeare.hamletQuote`
  * `shakespeare.kingRichardIIIQuote`
  * `shakespeare.romeoAndJulietQuote`
* `slackEmoji`
  * `slackEmoji.activity`
  * `slackEmoji.celebration`
  * `slackEmoji.custom`
  * `slackEmoji.emoji`
  * `slackEmoji.foodAndDrink`
  * `slackEmoji.nature`
  * `slackEmoji.objectsAndSymbols`
  * `slackEmoji.people`
  * `slackEmoji.travelAndPlaces`
* `space`
  * `space.agency`
  * `space.agencyAbbreviation`
  * `space.company`
  * `space.constellation`
  * `space.distanceMeasurement`
  * `space.galaxy`
  * `space.meteorite`
  * `space.moon`
  * `space.nasaSpaceCraft`
  * `space.nebula`
  * `space.planet`
  * `space.star`
  * `space.starCluster`
* `stock`
  * `stock.nsdqSymbol`
  * `stock.nyseSymbol`
* `superhero`
  * `superhero.descriptor`
  * `superhero.name`
  * `superhero.power`
  * `superhero.prefix`
  * `superhero.suffix`
* `team`
  * `team.creature`
  * `team.name`
  * `team.sport`
  * `team.state`
* `twinPeaks`
  * `twinPeaks.character`
  * `twinPeaks.location`
  * `twinPeaks.quote`
* `university`
  * `university.name`
  * `university.prefix`
  * `university.suffix`
* `witcher`
  * `witcher.character`
  * `witcher.location`
  * `witcher.monster`
  * `witcher.quote`
  * `witcher.school`
  * `witcher.witcher`
* `yoda`
  * `yoda.quote`
* `zelda`
  * `zelda.character`
  * `zelda.game`
</details>

```graphql
directive @fakeConfig(locale: String, seed: Int) on SCHEMA 
```

Customize fake data generation    

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
