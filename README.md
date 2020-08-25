# Azure App Services, Application Insights and Java agent

If you're running your Java app on Azure App Services and you'd like to get deeper insights on your application dependencies, logs etc. you might want to consider the [Azure Application Insights Java Agent](https://docs.microsoft.com/en-us/azure/azure-monitor/app/java-agent).

In principle, it's sufficient to include a reference to the Java agent to get things rolling. However, currently App Services doesn't provide a standard way of including the Applicaton Insights Java agent in its configuration, so the responsibilty of uploading the agent jar file lies with the developers. This is an example repository of how to bundle and configure the Application Insights Java agent using mainly Java tooling as part of the development process.

## Building and deploying the example

Let's start with declaring a few variables:

```bash
RG=...  # the target resource group, assuming that this has been created already
BASE_NAME=...  # i.e. myapp, choose something with less than 6 alphanumeric characters
APP_INSIGHTS_VERSION=...  # i.e. 2.5.1, the version of the app insights dependency
```

> As some of the Azure resources need to have globally unique names, the included ARM templates attempt to generate more or less unique names by appending a hash of the resource group name to the provided base name. If you prefer to have more control or need to use specific names, just update the variables in the templates.

The first step is to create the resources. If you already have an Azure App Services instance running, you might want to skip this. This repository contains an ARM template that creates a number of Azure resources and connects those, such as an Azure App Services instance to host the web app, a MySQL database, a Key Vault for storing secrets and of course an Application Insights instance.

```bash
WEBAPP=`az deployment group create -g $RG \
    --template-file infra/webapp-template-simple.json \
    --parameters baseName="$BASE_NAME" \
    --query properties.outputs.webAppName.value \
    -o tsv`
```

If you've cloned this repo, change directory to the `app` folder and run the following command to build the application. The pom file uses the passed variable to resolve the Application Insights jar dependency.

```bash
mvn clean package -Dapp.insights.version=$APP_INSIGHTS_VERSION
```

The above command also downloads and copies the necessary dependencies for the Java agent. These end up in the `target` directory, under the `resources` subdirectory.

Typically only a jar/war/ear file is deployed to App Services. However, in order to install the Java agent, we'll need to bundle it (separately) with the application jar file. In this example we'll explore two Maven based options for this purpose, first with the [assembly](https://maven.apache.org/plugins/maven-assembly-plugin/index.html) plugin, and alternatively with the [azure-web-app-maven](https://github.com/microsoft/azure-maven-plugins/wiki/Azure-Web-App) plugin. Note that these are not the only options, you could also go for a command line utility such as `zip` or other specific tasks in your CI pipeline.

### Maven Assembly plugin

According to its [docs](http://maven.apache.org/plugins-archives/maven-assembly-plugin-3.0.0/index.html) the *assembly* plugin is primarily intended to allow users to aggregate the project output along with its dependencies, modules, site documentation, and other files into a single distributable archive. There's a bunch of predefined specifications to bundle common artifacts, however, for our project we need to be very prescriptive and create a special zip file that contains all the required resources. The end result should look like this:

```bash
├── app.jar
└── resources
    ├── AI-Agent.xml
    └── applicationinsights-agent-2.5.1.jar
```

> Note that you can also put the agent at the top level, but it's best practice to put it in a specific directory together with its configuration.

In order to create such a file, we need to specify an assembly descriptor, which should be pretty trivial:

```xml
...
    <formats>
        <format>zip</format>
    </formats>
    <includeBaseDirectory>false</includeBaseDirectory> <!-- no root folder for the zip file-->
    <fileSets>
        <fileSet>
            <directory>${project.build.directory}</directory>
            <outputDirectory>.</outputDirectory> <!-- don't include target as a folder -->
            <includes>
                <include>app.jar</include>
                <include>resources/*</include>
            </includes>
        </fileSet>
    </fileSets>
...
```

It's possible to associate generation of the zip file with the package phase, but in this example we'll be explicitly executing that goal.

```bash
mvn assembly:single
```

The command will generate the zip file `app-service.zip` in the build directory. Next step is to deploy that using Azure CLI.

```bash
az webapp deployment source config-zip -g $RG -n $WEBAPP --src target/app-service.zip
```

There's also one more important piece of information that needs to be configured. The Application Insights agent jar needs to be specified as a startup option for the Java process. We can do that by setting the `JAVA_OPTS` application setting.

```bash
AGENT_CONFIG=-javaagent:D:/home/site/wwwroot/resources/applicationinsights-agent-$APP_INSIGHTS_VERSION.jar
az webapp config appsettings set -g $RG -n $WEBAPP --settings JAVA_OPTS="$AGENT_CONFIG" -o none
```

> A few remarks regarding the location of the agent jar, the zip deployment process unzips the contents of the zip file into the `$HOME/site/wwwroot` directory, and the javaagent option needs the absolute path for the agent jar. The included ARM template creates an App Services instance on a Windows plan, hence the prefix `D:/home`. For a Linux plan, you'd need to replace that with just `/home`. See the [Kudu docs](https://github.com/projectkudu/kudu/wiki/File-structure-on-azure) on the topic of the App Service File structure.

Now, you're ready to test the application.

### Azure Maven Web App plugin

If you don't want to use the assembly plugin or if you'd like to combine the packaging and deployment as a single step, you might want to try out the deployment capabalities of the [Azure Web App](https://github.com/microsoft/azure-maven-plugins/wiki/Azure-Web-App:-Deploy) Maven plugin.

The configuration for this plugin also includes the application settings (and it can also create new App Services instances), so you wouldn't need the Azure CLI commands to set those.

```xml
...
    <plugin>
        <groupId>com.microsoft.azure</groupId>
        <artifactId>azure-webapp-maven-plugin</artifactId>
        <version>1.9.1</version>
        <configuration>
            <schemaVersion>V2</schemaVersion>
            <resourceGroup>${resource.group.name}</resourceGroup>
            <appName>${webapp.name}</appName>
            <runtime>
                <os>windows</os>
                <javaVersion>1.8</javaVersion>
                <webContainer>java 8</webContainer>
            </runtime>
            <appSettings>
                <property>
                    <name>JAVA_OPTS</name>
                    <value>-javaagent:D:/home/site/wwwroot/resources/applicationinsights-agent-${app.insights.version}.jar</value>
                </property>
            </appSettings>
            <deploymentType>ZIP</deploymentType>
            <deployment>
                <resources>
                    <resource>
                        <directory>${project.build.directory}</directory>
                        <includes>
                            <include>app.jar</include>
                            <include>resources/*</include>
                        </includes>
                    </resource>
                </resources>
            </deployment>
        </configuration>
    </plugin>
...
```

Once you have your `pom.xml` configured, all you need to do is pass the relevant information and run the `deploy` goal.

```bash
mvn azure-webapp:deploy -Dresource.group.name=$RG -Dwebapp.name=$WEBAPP -Dapp.insights.version=$APP_INSIGHTS_VERSION
```

> You might notice an error message in your application logs when you're running this for the very first time, indicating that there's an error opening zip file or JAR manifest missing for the agent jar. That's because the deploy plugin first configures the application setting with the java agent startup option, while the agent jar file has not been extracted yet. The application will restart after the deployment, with the jar files present so you can safely ignore that. Subsequent deployments won't have this problem (unless there's a new version of the agent gets configured).

## Testing the functionality

The sample application is pretty basic, you can list registered *employees* by running the following command:

```bash
$ curl https://$WEBAPP.azurewebsites.net/api/employee
[]
```

The first time will return an empty list, you can insert new entries by *POST*ing them:

```bash
$ curl -X POST https://$WEBAPP.azurewebsites.net/api/employee -H "Content-Type: application/json" -d '{"alias":"meken", "firstName":"Murat", "lastName":"Eken"}'
{"id":1,"firstName":"Murat","lastName":"Eken","alias":"meken"}
```

After running a few of these you can tinker with Application Insights metrics/logs/dependency map to verify that the agent is sending telemetry to Application Insights. Note that it'll take a few minutes for the telemetry to be ingested by Application Insights, so if you don't immediately see anything, try it again after some time.

## Summary

This repository illustrates how to deploy the Application Insights agent jar and configure Azure App Services properly for a Spring Boot Java application using Maven tooling.
