# Kafka Tutorial Workshops

Code supporting meetup workshops based on [Kafka Tutorials](https://kafka-tutorials.confluent.io/).


## Introduction

This workshop is based on an upcoming feature in the 2.8 version of Kafka Streams, the [specific Kafka Streams uncaught exception handler](https://cwiki.apache.org/confluence/display/KAFKA/KIP-671%3A+Introduce+Kafka+Streams+Specific+Uncaught+Exception+Handler).  
Note that during this presentation, we'll show an example of using the new `StreamsUncaughtExceptionHandler`, but as of this presentation, Apache Kafka (R) hasn't been released yet.
So I'm going to recommend that you do not try this in a production setting until this feature is officially released.

### Workshop Steps

#### 1. Uncaught Exceptions in Kafka Streams

Kafka Streams provides support for some exceptions that users can encounter when running an application.
 1.  The `DeserializationExceptionHandler` can choose to fail or continue when there is a deserializing exception
 2. The `ProductionExceptionHandler` specifies how to handle an exception when attempting to produce a record.  Note there is [KIP-399](https://cwiki.apache.org/confluence/display/KAFKA/KIP-399%3A+Extend+ProductionExceptionHandler+to+cover+serialization+exceptions)
which proposes to add serialization errors to the scope of the `ProductionExceptionHandler`.
 3. Improved handling of [timeouts and retries](https://cwiki.apache.org/confluence/display/KAFKA/KIP-572%3A+Improve+timeouts+and+retries+in+Kafka+Streams) released over two versions 2.7 and 2.8

There are a couple of specific internal exceptions that Kafka Streams handles directly (`TaskCorruptedException`, `TaskMigratedException`), but any others bubble up and will kill the `StreamThread`
You can wrap all of your provided code with `try/catch` blocks, but exceptions from other sources are still possible.

In the 2.8 version of Kafka Streams introduces the  `StreamsUncaughtExceptionHandler` . Prior versions of Kafka Streams API allowed you to use the `Thread.UncaughtExceptionHandler`, which is Java API, but
only allows for clean-up or logging as the thread is already shutting down at that point.

The `StreamsUncaughtExceptionHandler` interface gives you an opportunity to respond to exceptions not handled by Kafka Streams.  
It has one method, `handle`, and it returns an `enum` of type `StreamThreadExceptionResponse` 
which provides you the opportunity to instruct Kafka Streams how to respond to the exception.  
There are three possible values: `REPLACE_THREAD`, `SHUTDOWN_CLIENT`, or `SHUTDOWN_APPLICATION`.




#### 2. Provision a new ccloud-stack on Confluent Cloud

This part assumes you have already set-up an account on [Confluent CLoud](https://confluent.cloud/) and you've installed the [Confluent Cloud CLI](https://docs.confluent.io/ccloud-cli/current/install.html). We're going to use the `ccloud-stack` utility to get everything set-up to work along with the workshop.  

A copy of the [ccloud_library.sh](https://github.com/confluentinc/examples/blob/latest/utils/ccloud_library.sh) is included in this repo and let's run this command now:

```
source ./ccloud_library.sh
```

Then let's create the stack of Confluent Cloud resources:

```
CLUSTER_CLOUD=aws
CLUSTER_REGION=us-west-2
ccloud::create_ccloud_stack
```

NOTE: Make sure you destroy all resources when the workshop concludes.

The `create` command generates a local config file, `java-service-account-NNNNN.config` when it completes. The `NNNNN` represents the service account id.  Take a quick look at the file:

```
cat stack-configs/java-service-account-*.config
```

You should see something like this:

```
# ENVIRONMENT ID: <ENVIRONMENT ID>
# SERVICE ACCOUNT ID: <SERVICE ACCOUNT ID>
# KAFKA CLUSTER ID: <KAFKA CLUSTER ID>
# SCHEMA REGISTRY CLUSTER ID: <SCHEMA REGISTRY CLUSTER ID>
# ------------------------------
ssl.endpoint.identification.algorithm=https
security.protocol=SASL_SSL
sasl.mechanism=PLAIN
bootstrap.servers=<BROKER ENDPOINT>
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="<API KEY>" password="<API SECRET>";
basic.auth.credentials.source=USER_INFO
schema.registry.basic.auth.user.info=<SR API KEY>:<SR API SECRET>
schema.registry.url=https://<SR ENDPOINT>
```

We'll use these properties for connecting to CCloud from a local connect instance and from the Kafka Streams application.  We'll get to that in just a minute.

Now that we have the cloud stack resources in place, let's create two topics we'll need to use during the workshop

```
ccloud kafka topic create input-topic
ccloud kafka topic create output-topic
```

#### 3. Properties setup, build, and run the Kafka Streams application

Earlier in the workshop when you created the `ccloud-stack` a local config file `java-service-account-NNNNN.config` was created as well.  You now need to add the contents of that config file to the properties the Kafka Streams app will use.  To add the configs run the following command:

```
cat stack-configs/java-service-account* >> configuration/dev.properties
```
You should see something like this (without the brackets and actual values instead)

```

application.id=kafka-streams-schedule-operations


input.topic.name=login-events
output.topic.name=output-topic

# ENVIRONMENT ID: <ENVIRONMENT ID>
# SERVICE ACCOUNT ID: <SERVICE ACCOUNT ID>
# KAFKA CLUSTER ID: <KAFKA CLUSTER ID>
# SCHEMA REGISTRY CLUSTER ID: <SCHEMA REGISTRY CLUSTER ID>
# ------------------------------
ssl.endpoint.identification.algorithm=https
security.protocol=SASL_SSL
sasl.mechanism=PLAIN
bootstrap.servers=<BROKER ENDPOINT>
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="<API KEY>" password="<API SECRET>";
basic.auth.credentials.source=USER_INFO
schema.registry.basic.auth.user.info=<SR API KEY>:<SR API SECRET>
schema.registry.url=https://<SR ENDPOINT>
```

Now with the configs in place, we can start the application. To build the application and create a jar file to run the Kafka Streams app execute the following:

```
./gradlew shadowJar
```

Now start the Kafka Streams application by executing:

```
java -jar build/libs/uncaught-exceptions-standalone-0.0.1.jar configuration/dev.properties
```

We'll see some log statements scroll across the screen.

Next, lets open a new terminal and produce some records and watch the results here.


#### 4. Produce some records

Now let's use the `ccloud cli` and produce a few records
first run this command:
```
ccloud kafka topic produce input-topic

```
then let's send some records:
```
 "All", "streams", "lead", "to", "Confluent", "Go", "to", "Kafka", "Summit"
```

Now go back to the streams terminal and observe the log output

#### 5. Review the output

```
ccloud kafka topic consume --print-keys --from-beginning
```

#### 6. Cloud UI Demo

Now we'll go to [Confluent Cloud](https://login.confluent.io/]) and login. Then we'll take a look at the CCloud UI and go over the available information. 


#### 7. Testing - "Look Ma no hands!"

Last, but not least, we should take a couple of minutes to talk about testing. 

Before we do that, let's take this 
opportunity to shut down your `ccloud-stack` resources.  Run the following command:

```
ccloud::destroy_ccloud_stack $SERVICE_ACCOUNT_ID
```
The `SERVICE_ACCOUNT_ID` was generated when you created the `ccloud-stack`.

So now we'll see how you can test locally, without the need for any of the resources we used in our example.  But our test code uses the same Kafka Streams application as written.  Run this command to test our code:

```
./gradlew test
```

The test should pass.  It's possible to unit-test with the exact Kafka Streams application because we're using the [ToplogyTestDriver](https://docs.confluent.io/5.5.0/streams/developer-guide/test-streams.html#testing-a-streams-application)

#### 8. Clean Up

If you haven't done so already, now is a good time to shut down all the resources we've created and started.  Because your Confluent Cloud cluster is using real cloud resources and is billable, delete the connector and clean up your Confluent Cloud environment when you complete this tutorial. You can use Confluent Cloud CLI or Confluent UI, but for this tutorial you can use the ccloud_library.sh library again. Pass in the `SERVICE_ACCOUNT_ID` that was generated when the `ccloud-stack was` created.

First clean up the `ccloud-stack`:

```
ccloud::destroy_ccloud_stack $SERVICE_ACCOUNT_ID
```
