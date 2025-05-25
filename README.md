# Exchange Rate Assignment
In this assignment, I was asked to create a small application that fetches currency exchange rates from multiple sources. Which provides an endpoint where the user can input the base currency and the currencies they want the exchange rates for.

To run this application, open the root directory in IntelliJ and select ExchangeRateService run configuration and press the green run button.

## Tools
I used Kotlin for this assignment as it is more modern and easier to use compared to Java.

For tools, I used ChatGPT to come up with a template for the application.

Ktor was used as the framework for this application was I needed a way to host the application server locally and I determined that Ktor was the right tool for the job as it is open-source, and it is supported by JetBrains.

## Main.kt
This file contains the core classes for the application.

ExchangeRateService class contains the logic for the fetching the data form the API's and averaging the exchange rates. This class has also taken consideration into a possible expansion in the future and made it easy to add new source APIs by separating the fetching and storage process for each API. The cache is also stored here to prevent the application from sending the same requests multiple times.

Metrics class contains the logic for the keeping up with how many times each API was called. By using a hash map, I was able to keep track of the number of times each API was used.

## MainTest.kt
This file contains the unit tests for the application.

The tests include the basic function tests for the exchange rate calculation, metrics data, and caching.

## Thoughts
After finishing this assignment, I came up with some potential improvements in the code.

- The cache could've been its own class. While its implementation is simple, but it isn't reliant on the exchange rate service itself.
- More unit tests for edge cases could've been added, such as how many symbols can it take for exchange rate service.