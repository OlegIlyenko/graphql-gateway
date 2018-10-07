**graphql-gateway** - SDL-based GraphQL gateway for REST and GraphQL-based micro-services

[![Build Status](https://travis-ci.org/OlegIlyenko/graphql-gateway.svg?branch=master)](https://travis-ci.org/OlegIlyenko/graphql-gateway)

⚠️ **Project is a POC and at very early experimental/WIP stage!** ⚠️

### Getting Started

You can use graphql-gateway as a docker container:

```bash
docker run -it -p 8080:8080 -v $(pwd):/schema tenshi/graphql-gateway
```

For you convenience, you can define an alias for it and put it in your `.bashrc`:

```bash
alias graphql-gateway='docker run -it -p 8080:8080 -v $(pwd):/schema tenshi/graphql-gateway'
```

After this is done, you can start the sever just by executing `graphql-gateway` - 
the server will automatically read the schema from all `*.graphql` files in a current 
working directory and watch them for changes.

There is also a fat JAR available on release notes: [graphql-gateway.jar](https://github.com/OlegIlyenko/graphql-gateway/releases/download/v0.1/graphql-gateway.jar).

### Configuration

Docker container accepts a number of environment variables:

* **General**
  * `PORT` - Int - server port (by default 8080)
  * `BIND_HOST` - String - bind host (by default 0.0.0.0)
  * `GRAPHIQL` - Boolean - enable/disable GraphiQL (by default true)
  * `INCLUDE_DIRECTIVES` - List of String - include specific directive sets (available directive sets are: http, graphql, faker, basic)
  * `EXCLUDE_DIRECTIVES` - List of String - exclude specific directive sets (available directive sets are: http, graphql, faker, basic)
* **Query tracing and logging**  
  * `SLOW_LOG_ENABLED` - Boolean - enable/disable logging of slow queries (by default true)
  * `SLOW_LOG_THRESHOLD` - FiniteDuration - SlowLog extension threshold (by default 10 seconds)
  * `SLOW_LOG_EXTENSION` - Boolean - enable/disable SlowLog GraphQL extension (by default false)
  * `SLOW_LOG_APOLLO_TRACING` - Boolean - enable/disable Apollo tracing GraphQL extension (by default false)
* **Schema live reloading**
  * `WATCH_ENABLED` - Boolean - enable/disable schema file reloader (by default true)
  * `WATCH_THRESHOLD` - FiniteDuration - internal poll interval for file watcher (by default 50 millis)
  * `WATCH_PATHS` - List of String - which directories to watch for schema files (by default ".")
  * `WATCH_GLOB` - List of String - which files are the schema files (by default "\*\*/\*.graphql")
* **Limits**  
  * `LIMIT_COMPLEXITY` - Double - query complexity limit (by default 10000)
  * `LIMIT_MAX_DEPTH` - Int - max query depth (by default 15)
  * `ALLOW_INTROSPECTION` - Boolean - enable/disable GraphQL introspect API (by default true)

The full configuration can be found in the [reference.conf](https://github.com/OlegIlyenko/graphql-gateway/blob/master/src/main/resources/reference.conf). 

### Supported SDL Directives

Schema definition is based on [GraphQL SDL](https://github.com/facebook/graphql/pull/90). SDL syntax allows you to define full GraphQL 
schema with interfaces, types, enums etc. In order to provide resolution logic for the fields, you can use directives described below. 
Directives will define how fields will behave. By default (if no directive is provided), field resolve function will treat a contextual 
value as a JSON object and will return its property with the same name. (check out an [example schema](https://github.com/OlegIlyenko/graphql-gateway/blob/master/testSchema.graphql))

#### `@httpGet`

```graphql
directive @httpGet(
  url: String!, 
  headers: [Header!],
  delegateHeaders: [String!] 
  query: [QueryParam!], 
  forAll: String) on FIELD_DEFINITION

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

#### `@includeSchema`

```graphql
directive @includeSchema(
  name: String!
  url: String!
  headers: [Header!],
  delegateHeaders: [String!] 
  query: [QueryParam!]
  oauth: OAuthClientCredentials) repeatable on SCHEMA

input OAuthClientCredentials {
  url: String!
  clientId: String!
  clientSecret: String!
  scopes: [String!]!
} 
```

Includes external GraphQL schema (based on GraphQL endpoint URL)

#### `@includeFields`

```graphql
directive @includeFields(
  "the name of the schema included with @includeGraphQL"
  schema: String!
  
  "the name of the type from the external schema" 
  type: String!
  
  "optional list of fields to include (of not provided, all fields are included)"    
  fields: [String!]
  
  "optional list of fields to exclude"    
  excludes: [String!]) repeatable on OBJECT
```

Adds fields loaded from the external GraphQL schema.

#### `@fake`

```graphql
directive @fake(expr: String, type: FakeType, min: Int, max: Int, past: Boolean, future: Boolean) on FIELD_DEFINITION 
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
  name: String! @fake(expr: "name.fullName")
  address: String! @fake(expr: "#{address.fullAddress} - #{numerify 'SOME######'}")
  info: Info! @fake
}
``` 

<details>
  <summary>`FakeType` GraphQL SDL</summary>

```graphql
"The type of the content generated by the faker"
enum FakeType {
  "Represents faker expression `address.buildingNumber`."
  AddressBuildingNumber

  "Represents faker expression `address.city`."
  AddressCity

  "Represents faker expression `address.cityName`."
  AddressCityName

  "Represents faker expression `address.cityPrefix`."
  AddressCityPrefix

  "Represents faker expression `address.citySuffix`."
  AddressCitySuffix

  "Represents faker expression `address.country`."
  AddressCountry

  "Represents faker expression `address.countryCode`."
  AddressCountryCode

  "Represents faker expression `address.firstName`."
  AddressFirstName

  "Represents faker expression `address.fullAddress`."
  AddressFullAddress

  "Represents faker expression `address.lastName`."
  AddressLastName

  "Represents faker expression `address.latitude`."
  AddressLatitude

  "Represents faker expression `address.longitude`."
  AddressLongitude

  "Represents faker expression `address.secondaryAddress`."
  AddressSecondaryAddress

  "Represents faker expression `address.state`."
  AddressState

  "Represents faker expression `address.stateAbbr`."
  AddressStateAbbr

  "Represents faker expression `address.streetAddress`."
  AddressStreetAddress

  "Represents faker expression `address.streetAddressNumber`."
  AddressStreetAddressNumber

  "Represents faker expression `address.streetName`."
  AddressStreetName

  "Represents faker expression `address.streetPrefix`."
  AddressStreetPrefix

  "Represents faker expression `address.streetSuffix`."
  AddressStreetSuffix

  "Represents faker expression `address.timeZone`."
  AddressTimeZone

  "Represents faker expression `address.zipCode`."
  AddressZipCode

  "Represents faker expression `ancient.god`."
  AncientGod

  "Represents faker expression `ancient.hero`."
  AncientHero

  "Represents faker expression `ancient.primordial`."
  AncientPrimordial

  "Represents faker expression `ancient.titan`."
  AncientTitan

  "Represents faker expression `app.author`."
  AppAuthor

  "Represents faker expression `app.name`."
  AppName

  "Represents faker expression `app.version`."
  AppVersion

  "Represents faker expression `artist.name`."
  ArtistName

  "Represents faker expression `avatar.image`."
  AvatarImage

  "Represents faker expression `beer.hop`."
  BeerHop

  "Represents faker expression `beer.malt`."
  BeerMalt

  "Represents faker expression `beer.name`."
  BeerName

  "Represents faker expression `beer.style`."
  BeerStyle

  "Represents faker expression `beer.yeast`."
  BeerYeast

  "Represents faker expression `book.author`."
  BookAuthor

  "Represents faker expression `book.genre`."
  BookGenre

  "Represents faker expression `book.publisher`."
  BookPublisher

  "Represents faker expression `book.title`."
  BookTitle

  "Represents faker expression `bool.bool`."
  BoolBool

  "Represents faker expression `business.creditCardExpiry`."
  BusinessCreditCardExpiry

  "Represents faker expression `business.creditCardNumber`."
  BusinessCreditCardNumber

  "Represents faker expression `business.creditCardType`."
  BusinessCreditCardType

  "Represents faker expression `cat.breed`."
  CatBreed

  "Represents faker expression `cat.name`."
  CatName

  "Represents faker expression `cat.registry`."
  CatRegistry

  "Represents faker expression `chuckNorris.fact`."
  ChuckNorrisFact

  "Represents faker expression `code.asin`."
  CodeAsin

  "Represents faker expression `code.ean13`."
  CodeEan13

  "Represents faker expression `code.ean8`."
  CodeEan8

  "Represents faker expression `code.gtin13`."
  CodeGtin13

  "Represents faker expression `code.gtin8`."
  CodeGtin8

  "Represents faker expression `code.imei`."
  CodeImei

  "Represents faker expression `code.isbn10`."
  CodeIsbn10

  "Represents faker expression `code.isbn13`."
  CodeIsbn13

  "Represents faker expression `code.isbnGroup`."
  CodeIsbnGroup

  "Represents faker expression `code.isbnGs1`."
  CodeIsbnGs1

  "Represents faker expression `code.isbnRegistrant`."
  CodeIsbnRegistrant

  "Represents faker expression `color.name`."
  ColorName

  "Represents faker expression `commerce.color`."
  CommerceColor

  "Represents faker expression `commerce.department`."
  CommerceDepartment

  "Represents faker expression `commerce.material`."
  CommerceMaterial

  "Represents faker expression `commerce.price`."
  CommercePrice

  "Represents faker expression `commerce.productName`."
  CommerceProductName

  "Represents faker expression `commerce.promotionCode`."
  CommercePromotionCode

  "Represents faker expression `company.bs`."
  CompanyBs

  "Represents faker expression `company.buzzword`."
  CompanyBuzzword

  "Represents faker expression `company.catchPhrase`."
  CompanyCatchPhrase

  "Represents faker expression `company.industry`."
  CompanyIndustry

  "Represents faker expression `company.logo`."
  CompanyLogo

  "Represents faker expression `company.name`."
  CompanyName

  "Represents faker expression `company.profession`."
  CompanyProfession

  "Represents faker expression `company.suffix`."
  CompanySuffix

  "Represents faker expression `company.url`."
  CompanyUrl

  "Represents faker expression `crypto.md5`."
  CryptoMd5

  "Represents faker expression `crypto.sha1`."
  CryptoSha1

  "Represents faker expression `crypto.sha256`."
  CryptoSha256

  "Represents faker expression `crypto.sha512`."
  CryptoSha512

  "Represents faker expression `currency.code`."
  CurrencyCode

  "Represents faker expression `currency.name`."
  CurrencyName

  "Represents faker expression `demographic.demonym`."
  DemographicDemonym

  "Represents faker expression `demographic.educationalAttainment`."
  DemographicEducationalAttainment

  "Represents faker expression `demographic.maritalStatus`."
  DemographicMaritalStatus

  "Represents faker expression `demographic.race`."
  DemographicRace

  "Represents faker expression `demographic.sex`."
  DemographicSex

  "Represents faker expression `dog.age`."
  DogAge

  "Represents faker expression `dog.breed`."
  DogBreed

  "Represents faker expression `dog.coatLength`."
  DogCoatLength

  "Represents faker expression `dog.gender`."
  DogGender

  "Represents faker expression `dog.memePhrase`."
  DogMemePhrase

  "Represents faker expression `dog.name`."
  DogName

  "Represents faker expression `dog.size`."
  DogSize

  "Represents faker expression `dog.sound`."
  DogSound

  "Represents faker expression `dragonBall.character`."
  DragonBallCharacter

  "Represents faker expression `educator.campus`."
  EducatorCampus

  "Represents faker expression `educator.course`."
  EducatorCourse

  "Represents faker expression `educator.secondarySchool`."
  EducatorSecondarySchool

  "Represents faker expression `educator.university`."
  EducatorUniversity

  "Represents faker expression `esports.event`."
  EsportsEvent

  "Represents faker expression `esports.game`."
  EsportsGame

  "Represents faker expression `esports.league`."
  EsportsLeague

  "Represents faker expression `esports.player`."
  EsportsPlayer

  "Represents faker expression `esports.team`."
  EsportsTeam

  "Represents faker expression `file.extension`."
  FileExtension

  "Represents faker expression `file.fileName`."
  FileFileName

  "Represents faker expression `file.mimeType`."
  FileMimeType

  "Represents faker expression `finance.bic`."
  FinanceBic

  "Represents faker expression `finance.creditCard`."
  FinanceCreditCard

  "Represents faker expression `finance.iban`."
  FinanceIban

  "Represents faker expression `food.ingredient`."
  FoodIngredient

  "Represents faker expression `food.measurement`."
  FoodMeasurement

  "Represents faker expression `food.spice`."
  FoodSpice

  "Represents faker expression `friends.character`."
  FriendsCharacter

  "Represents faker expression `friends.location`."
  FriendsLocation

  "Represents faker expression `friends.quote`."
  FriendsQuote

  "Represents faker expression `funnyName.name`."
  FunnyNameName

  "Represents faker expression `gameOfThrones.character`."
  GameOfThronesCharacter

  "Represents faker expression `gameOfThrones.city`."
  GameOfThronesCity

  "Represents faker expression `gameOfThrones.dragon`."
  GameOfThronesDragon

  "Represents faker expression `gameOfThrones.house`."
  GameOfThronesHouse

  "Represents faker expression `gameOfThrones.quote`."
  GameOfThronesQuote

  "Represents faker expression `hacker.abbreviation`."
  HackerAbbreviation

  "Represents faker expression `hacker.adjective`."
  HackerAdjective

  "Represents faker expression `hacker.ingverb`."
  HackerIngverb

  "Represents faker expression `hacker.noun`."
  HackerNoun

  "Represents faker expression `hacker.verb`."
  HackerVerb

  "Represents faker expression `harryPotter.book`."
  HarryPotterBook

  "Represents faker expression `harryPotter.character`."
  HarryPotterCharacter

  "Represents faker expression `harryPotter.location`."
  HarryPotterLocation

  "Represents faker expression `harryPotter.quote`."
  HarryPotterQuote

  "Represents faker expression `hipster.word`."
  HipsterWord

  "Represents faker expression `hitchhikersGuideToTheGalaxy.character`."
  HitchhikersGuideToTheGalaxyCharacter

  "Represents faker expression `hitchhikersGuideToTheGalaxy.location`."
  HitchhikersGuideToTheGalaxyLocation

  "Represents faker expression `hitchhikersGuideToTheGalaxy.marvinQuote`."
  HitchhikersGuideToTheGalaxyMarvinQuote

  "Represents faker expression `hitchhikersGuideToTheGalaxy.planet`."
  HitchhikersGuideToTheGalaxyPlanet

  "Represents faker expression `hitchhikersGuideToTheGalaxy.quote`."
  HitchhikersGuideToTheGalaxyQuote

  "Represents faker expression `hitchhikersGuideToTheGalaxy.specie`."
  HitchhikersGuideToTheGalaxySpecie

  "Represents faker expression `hitchhikersGuideToTheGalaxy.starship`."
  HitchhikersGuideToTheGalaxyStarship

  "Represents faker expression `hobbit.character`."
  HobbitCharacter

  "Represents faker expression `hobbit.location`."
  HobbitLocation

  "Represents faker expression `hobbit.quote`."
  HobbitQuote

  "Represents faker expression `hobbit.thorinsCompany`."
  HobbitThorinsCompany

  "Represents faker expression `howIMetYourMother.catchPhrase`."
  HowIMetYourMotherCatchPhrase

  "Represents faker expression `howIMetYourMother.character`."
  HowIMetYourMotherCharacter

  "Represents faker expression `howIMetYourMother.highFive`."
  HowIMetYourMotherHighFive

  "Represents faker expression `howIMetYourMother.quote`."
  HowIMetYourMotherQuote

  "Represents faker expression `idNumber.invalid`."
  IdNumberInvalid

  "Represents faker expression `idNumber.invalidSvSeSsn`."
  IdNumberInvalidSvSeSsn

  "Represents faker expression `idNumber.ssnValid`."
  IdNumberSsnValid

  "Represents faker expression `idNumber.valid`."
  IdNumberValid

  "Represents faker expression `idNumber.validSvSeSsn`."
  IdNumberValidSvSeSsn

  "Represents faker expression `internet.avatar`."
  InternetAvatar

  "Represents faker expression `internet.domainName`."
  InternetDomainName

  "Represents faker expression `internet.domainSuffix`."
  InternetDomainSuffix

  "Represents faker expression `internet.domainWord`."
  InternetDomainWord

  "Represents faker expression `internet.emailAddress`."
  InternetEmailAddress

  "Represents faker expression `internet.image`."
  InternetImage

  "Represents faker expression `internet.ipV4Address`."
  InternetIpV4Address

  "Represents faker expression `internet.ipV4Cidr`."
  InternetIpV4Cidr

  "Represents faker expression `internet.ipV6Address`."
  InternetIpV6Address

  "Represents faker expression `internet.ipV6Cidr`."
  InternetIpV6Cidr

  "Represents faker expression `internet.macAddress`."
  InternetMacAddress

  "Represents faker expression `internet.password`."
  InternetPassword

  "Represents faker expression `internet.privateIpV4Address`."
  InternetPrivateIpV4Address

  "Represents faker expression `internet.publicIpV4Address`."
  InternetPublicIpV4Address

  "Represents faker expression `internet.safeEmailAddress`."
  InternetSafeEmailAddress

  "Represents faker expression `internet.slug`."
  InternetSlug

  "Represents faker expression `internet.url`."
  InternetUrl

  "Represents faker expression `internet.uuid`."
  InternetUuid

  "Represents faker expression `job.field`."
  JobField

  "Represents faker expression `job.keySkills`."
  JobKeySkills

  "Represents faker expression `job.position`."
  JobPosition

  "Represents faker expression `job.seniority`."
  JobSeniority

  "Represents faker expression `job.title`."
  JobTitle

  "Represents faker expression `leagueOfLegends.champion`."
  LeagueOfLegendsChampion

  "Represents faker expression `leagueOfLegends.location`."
  LeagueOfLegendsLocation

  "Represents faker expression `leagueOfLegends.masteries`."
  LeagueOfLegendsMasteries

  "Represents faker expression `leagueOfLegends.quote`."
  LeagueOfLegendsQuote

  "Represents faker expression `leagueOfLegends.rank`."
  LeagueOfLegendsRank

  "Represents faker expression `leagueOfLegends.summonerSpell`."
  LeagueOfLegendsSummonerSpell

  "Represents faker expression `lebowski.actor`."
  LebowskiActor

  "Represents faker expression `lebowski.character`."
  LebowskiCharacter

  "Represents faker expression `lebowski.quote`."
  LebowskiQuote

  "Represents faker expression `lordOfTheRings.character`."
  LordOfTheRingsCharacter

  "Represents faker expression `lordOfTheRings.location`."
  LordOfTheRingsLocation

  "Represents faker expression `lorem.character`."
  LoremCharacter

  "Represents faker expression `lorem.characters`."
  LoremCharacters

  "Represents faker expression `lorem.paragraph`."
  LoremParagraph

  "Represents faker expression `lorem.sentence`."
  LoremSentence

  "Represents faker expression `lorem.word`."
  LoremWord

  "Represents faker expression `matz.quote`."
  MatzQuote

  "Represents faker expression `music.chord`."
  MusicChord

  "Represents faker expression `music.instrument`."
  MusicInstrument

  "Represents faker expression `music.key`."
  MusicKey

  "Represents faker expression `name.firstName`."
  NameFirstName

  "Represents faker expression `name.fullName`."
  NameFullName

  "Represents faker expression `name.lastName`."
  NameLastName

  "Represents faker expression `name.name`."
  NameName

  "Represents faker expression `name.nameWithMiddle`."
  NameNameWithMiddle

  "Represents faker expression `name.prefix`."
  NamePrefix

  "Represents faker expression `name.suffix`."
  NameSuffix

  "Represents faker expression `name.title`."
  NameTitle

  "Represents faker expression `name.username`."
  NameUsername

  "Represents faker expression `number.digit`."
  NumberDigit

  "Represents faker expression `number.randomDigit`."
  NumberRandomDigit

  "Represents faker expression `number.randomDigitNotZero`."
  NumberRandomDigitNotZero

  "Represents faker expression `number.randomNumber`."
  NumberRandomNumber

  "Represents faker expression `overwatch.hero`."
  OverwatchHero

  "Represents faker expression `overwatch.location`."
  OverwatchLocation

  "Represents faker expression `overwatch.quote`."
  OverwatchQuote

  "Represents faker expression `phoneNumber.cellPhone`."
  PhoneNumberCellPhone

  "Represents faker expression `phoneNumber.phoneNumber`."
  PhoneNumberPhoneNumber

  "Represents faker expression `pokemon.location`."
  PokemonLocation

  "Represents faker expression `pokemon.name`."
  PokemonName

  "Represents faker expression `random.nextDouble`."
  RandomNextDouble

  "Represents faker expression `random.nextLong`."
  RandomNextLong

  "Represents faker expression `rickAndMorty.character`."
  RickAndMortyCharacter

  "Represents faker expression `rickAndMorty.location`."
  RickAndMortyLocation

  "Represents faker expression `rickAndMorty.quote`."
  RickAndMortyQuote

  "Represents faker expression `robin.quote`."
  RobinQuote

  "Represents faker expression `rockBand.name`."
  RockBandName

  "Represents faker expression `shakespeare.asYouLikeItQuote`."
  ShakespeareAsYouLikeItQuote

  "Represents faker expression `shakespeare.hamletQuote`."
  ShakespeareHamletQuote

  "Represents faker expression `shakespeare.kingRichardIIIQuote`."
  ShakespeareKingRichardIIIQuote

  "Represents faker expression `shakespeare.romeoAndJulietQuote`."
  ShakespeareRomeoAndJulietQuote

  "Represents faker expression `slackEmoji.activity`."
  SlackEmojiActivity

  "Represents faker expression `slackEmoji.celebration`."
  SlackEmojiCelebration

  "Represents faker expression `slackEmoji.custom`."
  SlackEmojiCustom

  "Represents faker expression `slackEmoji.emoji`."
  SlackEmojiEmoji

  "Represents faker expression `slackEmoji.foodAndDrink`."
  SlackEmojiFoodAndDrink

  "Represents faker expression `slackEmoji.nature`."
  SlackEmojiNature

  "Represents faker expression `slackEmoji.objectsAndSymbols`."
  SlackEmojiObjectsAndSymbols

  "Represents faker expression `slackEmoji.people`."
  SlackEmojiPeople

  "Represents faker expression `slackEmoji.travelAndPlaces`."
  SlackEmojiTravelAndPlaces

  "Represents faker expression `space.agency`."
  SpaceAgency

  "Represents faker expression `space.agencyAbbreviation`."
  SpaceAgencyAbbreviation

  "Represents faker expression `space.company`."
  SpaceCompany

  "Represents faker expression `space.constellation`."
  SpaceConstellation

  "Represents faker expression `space.distanceMeasurement`."
  SpaceDistanceMeasurement

  "Represents faker expression `space.galaxy`."
  SpaceGalaxy

  "Represents faker expression `space.meteorite`."
  SpaceMeteorite

  "Represents faker expression `space.moon`."
  SpaceMoon

  "Represents faker expression `space.nasaSpaceCraft`."
  SpaceNasaSpaceCraft

  "Represents faker expression `space.nebula`."
  SpaceNebula

  "Represents faker expression `space.planet`."
  SpacePlanet

  "Represents faker expression `space.star`."
  SpaceStar

  "Represents faker expression `space.starCluster`."
  SpaceStarCluster

  "Represents faker expression `starTrek.character`."
  StarTrekCharacter

  "Represents faker expression `starTrek.location`."
  StarTrekLocation

  "Represents faker expression `starTrek.specie`."
  StarTrekSpecie

  "Represents faker expression `starTrek.villain`."
  StarTrekVillain

  "Represents faker expression `stock.nsdqSymbol`."
  StockNsdqSymbol

  "Represents faker expression `stock.nyseSymbol`."
  StockNyseSymbol

  "Represents faker expression `superhero.descriptor`."
  SuperheroDescriptor

  "Represents faker expression `superhero.name`."
  SuperheroName

  "Represents faker expression `superhero.power`."
  SuperheroPower

  "Represents faker expression `superhero.prefix`."
  SuperheroPrefix

  "Represents faker expression `superhero.suffix`."
  SuperheroSuffix

  "Represents faker expression `team.creature`."
  TeamCreature

  "Represents faker expression `team.name`."
  TeamName

  "Represents faker expression `team.sport`."
  TeamSport

  "Represents faker expression `team.state`."
  TeamState

  "Represents faker expression `twinPeaks.character`."
  TwinPeaksCharacter

  "Represents faker expression `twinPeaks.location`."
  TwinPeaksLocation

  "Represents faker expression `twinPeaks.quote`."
  TwinPeaksQuote

  "Represents faker expression `university.name`."
  UniversityName

  "Represents faker expression `university.prefix`."
  UniversityPrefix

  "Represents faker expression `university.suffix`."
  UniversitySuffix

  "Represents faker expression `weather.description`."
  WeatherDescription

  "Represents faker expression `weather.temperatureCelsius`."
  WeatherTemperatureCelsius

  "Represents faker expression `weather.temperatureFahrenheit`."
  WeatherTemperatureFahrenheit

  "Represents faker expression `witcher.character`."
  WitcherCharacter

  "Represents faker expression `witcher.location`."
  WitcherLocation

  "Represents faker expression `witcher.monster`."
  WitcherMonster

  "Represents faker expression `witcher.quote`."
  WitcherQuote

  "Represents faker expression `witcher.school`."
  WitcherSchool

  "Represents faker expression `witcher.witcher`."
  WitcherWitcher

  "Represents faker expression `yoda.quote`."
  YodaQuote

  "Represents faker expression `zelda.character`."
  ZeldaCharacter

  "Represents faker expression `zelda.game`."
  ZeldaGame
}
```
</details>

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
  * `address.streetAddress`
  * `address.streetAddress(includeSecondary: Boolean)`
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
* `avatar`
  * `avatar.image`
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
* `currency`
  * `currency.code`
  * `currency.name`
* `date`
* `demographic`
  * `demographic.demonym`
  * `demographic.educationalAttainment`
  * `demographic.maritalStatus`
  * `demographic.race`
  * `demographic.sex`
* `dog`
  * `dog.age`
  * `dog.breed`
  * `dog.coatLength`
  * `dog.gender`
  * `dog.memePhrase`
  * `dog.name`
  * `dog.size`
  * `dog.sound`
* `dragonBall`
  * `dragonBall.character`
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
  * `finance.creditCard(creditCardType: CreditCardType)`
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
* `funnyName`
  * `funnyName.name`
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
* `hitchhikersGuideToTheGalaxy`
  * `hitchhikersGuideToTheGalaxy.character`
  * `hitchhikersGuideToTheGalaxy.location`
  * `hitchhikersGuideToTheGalaxy.marvinQuote`
  * `hitchhikersGuideToTheGalaxy.planet`
  * `hitchhikersGuideToTheGalaxy.quote`
  * `hitchhikersGuideToTheGalaxy.specie`
  * `hitchhikersGuideToTheGalaxy.starship`
* `hobbit`
  * `hobbit.character`
  * `hobbit.location`
  * `hobbit.quote`
  * `hobbit.thorinsCompany`
* `howIMetYourMother`
  * `howIMetYourMother.catchPhrase`
  * `howIMetYourMother.character`
  * `howIMetYourMother.highFive`
  * `howIMetYourMother.quote`
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
  * `internet.image`

    Generates a random image url based on the lorempixel service. All the images provided by this service are released under the creative commons license (CC BY-SA). For more information, please visit: http://lorempixel.com/
  * `internet.image(width: Int, height: Int, gray: java.lang.Boolean, text: String)`

    Same as image() but allows client code to choose a few image characteristics
  * `internet.ipV4Address`

    returns an IPv4 address in dot separated octets.
  * `internet.ipV4Cidr`
  * `internet.ipV6Address`

    Returns an IPv6 address in hh:hh:hh:hh:hh:hh:hh:hh format.
  * `internet.ipV6Cidr`
  * `internet.macAddress(prefix: String)`

    Returns a MAC address in the following format: 6-bytes in MM:MM:MM:SS:SS:SS format.
  * `internet.macAddress`
  * `internet.password(minimumLength: Int, maximumLength: Int, includeUppercase: Boolean, includeSpecial: Boolean)`
  * `internet.password(minimumLength: Int, maximumLength: Int, includeUppercase: Boolean)`
  * `internet.password`
  * `internet.password(minimumLength: Int, maximumLength: Int)`
  * `internet.privateIpV4Address`
  * `internet.publicIpV4Address`
  * `internet.safeEmailAddress(localPart: String)`
  * `internet.safeEmailAddress`
  * `internet.slug(wordsOrNull: [String], glueOrNull: String)`
  * `internet.slug`
  * `internet.url`
  * `internet.uuid`

    Returns a UUID (type 4) as String.
* `job`
  * `job.field`
  * `job.keySkills`
  * `job.position`
  * `job.seniority`
  * `job.title`
* `leagueOfLegends`
  * `leagueOfLegends.champion`
  * `leagueOfLegends.location`
  * `leagueOfLegends.masteries`
  * `leagueOfLegends.quote`
  * `leagueOfLegends.rank`
  * `leagueOfLegends.summonerSpell`
* `lebowski`
  * `lebowski.actor`
  * `lebowski.character`
  * `lebowski.quote`
* `lordOfTheRings`
  * `lordOfTheRings.character`
  * `lordOfTheRings.location`
* `lorem`
  * `lorem.character(includeUppercase: Boolean)`
  * `lorem.character`
  * `lorem.characters(includeUppercase: Boolean)`
  * `lorem.characters(minimumLength: Int, maximumLength: Int, includeUppercase: Boolean)`
  * `lorem.characters(minimumLength: Int, maximumLength: Int)`
  * `lorem.characters`
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
  * `number.randomNumber`

    Returns a random number
  * `number.randomNumber(numberOfDigits: Int, strict: Boolean)`
* `options`
* `overwatch`
  * `overwatch.hero`
  * `overwatch.location`
  * `overwatch.quote`
* `phoneNumber`
  * `phoneNumber.cellPhone`
  * `phoneNumber.phoneNumber`
* `pokemon`
  * `pokemon.location`
  * `pokemon.name`
* `random`
  * `random.nextDouble`
  * `random.nextInt(min: Int, max: Int)`
  * `random.nextInt(n: Int)`
  * `random.nextLong(n: long)`
  * `random.nextLong`
* `rickAndMorty`
  * `rickAndMorty.character`
  * `rickAndMorty.location`
  * `rickAndMorty.quote`
* `robin`
  * `robin.quote`
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
* `starTrek`
  * `starTrek.character`
  * `starTrek.location`
  * `starTrek.specie`
  * `starTrek.villain`
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
* `weather`
  * `weather.description`

    Generates a short weather description.
  * `weather.temperatureCelsius(minTemperature: Int, maxTemperature: Int)`

    Generates a random temperature celsius between two temperatures.
  * `weather.temperatureCelsius`

    Generates a random temperature celsius between -30 and 38 degrees.
  * `weather.temperatureFahrenheit`

    Generates a random temperature fahrenheit between -22 and 100 degrees.
  * `weather.temperatureFahrenheit(minTemperature: Int, maxTemperature: Int)`

    Generates a random temperature fahrenheit between two temperatures.
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

#### `@fakeConfig`

```graphql
directive @fakeConfig(locale: String, seed: Int) on SCHEMA 
```

Customize fake data generation    

#### `@const`

```graphql
directive @const(value: Any!) on FIELD_DEFINITION | SCHEMA
```
            
Provides a way to resolve a field with a constant value. `value` can be any valid GraphQL input value. It would be treated as a JSON value.

#### `@jsonConst`

```graphql
directive @jsonConst(value: String!) on FIELD_DEFINITION | SCHEMA
```
            
Provides a way to resolve a field with a constant value. `value` should be a valid JSON value.

#### `@arg`

```graphql
directive @arg(name: String!) on FIELD_DEFINITION
```
            
Provides a way to resolve a field with value of one of its arguments.

#### `@value`

```graphql
directive @value(name: String, path: String) on FIELD_DEFINITION
```
            
Extracts a value(s) from the context object. It supports following extractors via arguments (only one can be used):

* `name` - Extracts a named property value from a context JSON object
* `path` - A [JSON Path](http://goessner.net/articles/JsonPath/) expression. It would be executed against current context JSON value.

#### `@context`

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
            
The placeholder consists of two parts separated by dot (`.`): the scope (`value` in this case) and the extractor (`$.results[0].film`) - a JSON Path extractor in this example). The scope defines a place/value from which you would like extract a value. Following scopes are supported:

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

**Publishing**

The docker publishing is done with sbt-native-packager plugin:

```bash
sbt docker:publishLocal

# or

sbt docker:publish
```

Fat JAR is created with sbt-assembly plugin:

```bash
sbt assembly
```

## License

**graphql-gateway** is licensed under [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
