# GitHub Ranks

## Table of Contents

1. [Introduction](#introduction)
2. [Configuration](#configuration)
3. [Development and Running](#development-and-running)
4. [Testing](#testing)
5. [API](#api)
6. [Notes](#notes-and-points-to-improve)
7. [Contributing](#contributing)
8. [License](#license)

## Introduction

GitHub Ranks is a web backend application, providing contributor statistics of organizations on [github.com](https://github.com).

## Configuration

Application can be configured via [application.conf](src/main/resources/application.conf) for running locally. You can also override config values with following environment variables.

| Variable Name | Data Type | Description                       | Required                  |
| ------------- | --------- | --------------------------------- | ------------------------- |
| HOST          | Int       | Running host of application       | No, defaults to `0.0.0.0` |
| PORT          | Int       | Running port of application       | No, defaults to `8080`    |
| GH_TOKEN      | String    | OAuth token to access GitHub APIs | Yes                       |

Secret values like tokens can also be placed in `secret.conf` file you can create under `src/main/resources` for development. It is git-ignored by default.

## Development and Running

Application is built with SBT. So, standard SBT tasks like `clean`, `compile` and `run` can be used. To run the application locally:

```bash
sbt run
```

You may also run the application in a Docker container. To build an image locally

```bash
sbt 'Docker / publishLocal'
```

To start a container from Docker image (also exposing port and providing GH_TOKEN environment)

```bash
docker run --rm -p 8080:8080 -e GH_TOKEN=REPLACE_ME github-ranks:0.1
```

## Testing

To run automated tests, you can use `test` and `testOnly` tasks of SBT. To run all tests:

```bash
sbt test
```

To run specific test(s):

```bash
sbt 'testOnly fullyQualifiedTestClassName1 fullyQualifiedTestClassName2 ...'
```

## API

Here is an overview of the APIs:

| Method | URL                              | Link                                     |
| ------ | -------------------------------- | ---------------------------------------- |
| GET    | /org/`organization`/contributors | [Jump](#get-orgorganizationcontributors) |

Errors return an error Json in following format:

```json
{
  "error": "Some human readable description of the error"
}
```

with a corresponding HTTP status code depending on the error.

All successful responses will have `200 OK` status unless explicitly mentioned.

---

### GET /org/`organization`/contributors

Returns a list of all contributors of all repositories under `organization`, sorted in descending order by their total contributions

#### Example Successful Response for `/org/zio/contributors`

```json
[
    {
        "contributions": 3936,
        "login": "scala-steward"
    },
    {
        "contributions": 1061,
        "login": "jdegoes"
    },
    {
        "contributions": 956,
        "login": "adamgfraser"
    },
    {
        "contributions": 620,
        "login": "mijicd"
    },
    {
        "contributions": 494,
        "login": "jczuchnowski"
    },
    {
        "contributions": 403,
        "login": "Regis Kuckaertz <regis.kuckaertz@theguardian.com>"
    },
    ...
]
```

#### Possible Errors

| What               | When                                             | Status |
| ------------------ | ------------------------------------------------ | ------ |
| Rate limited       | Requests to GitHub are rate limited              | 403    |
| Not found          | Organization/repository is not found on GitHub   | 404    |
| Unhandled          | Any unhandled error                              | 500    |
| Unavailable        | Cannot talk to GitHub (network/parse error etc.) | 503    |

## Notes and Points to Improve

* When at least one of the parallel requests fails, the rest of the requests that have been completed successfully become wasted as their results are thrown away because an error response is produced.
  * This could be achieved by having a response object that contains both already collected data and the error if any. This way users of the API can get partial results.
* HTTP requests are done every time.
  * Results of successful HTTP requests could be cached as contributors of repositories/organizations don't change that often. This would help avoid this network overhead and improve performance.
* If `Docker / publishLocal` fails, you may try using a different base image. See `dockerBaseImage` key in [build.sbt](build.sbt).

## Contributing

All contributions are welcome. Please feel free to send a pull request. Thank you.

## License

GitHub Ranks is licensed with [MIT License](LICENSE.md).
