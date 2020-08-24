# Azure App Services, Application Insights and Java agent

If you're running your Java app on Azure App Services and you'd like to get deeper insights on your application dependencies, logs etc. you might want to consider the [Azure Application Insights Java Agent](https://docs.microsoft.com/en-us/azure/azure-monitor/app/java-agent).

In principle, it's sufficient to include a reference to the Java agent to get things rolling. However, currently App Services doesn't provide a standard way of including the Applicaton Insights Java agent in its configuration, so the responsibilty of uploading the agent jar file lies with the developers. This is an example repository of how to bundle and configure the Application Insights Java agent using mainly Java tooling as part of the development process.

## Running the example

Let's start with declaring a few variables:

```bash
RG=...  # the target resource group, assuming that this has been created already
BASE_NAME=...  # i.e. myapp, choose something with less than 6 alphanumeric characters
APP_INSIGHTS_VERSION=...  # i.e. 2.5.1, the version of the app insights dependency
```

> As some of the Azure resources need to have globally unique names, the included ARM templates attempt to generate more or less unique names by appending a hash of the resource group name to the provided base name. If you prefer to have more control or need to use specific names, just update the variables in the templates.

The first step is to create the resources. If you already have an Azure App Services instance running, you might want to skip this. This repository contains an ARM template that creates a number of Azure resources and connects those, such as an Azure App Service instance to host the web app, a MySQL database, a Key Vault for storing secrets and of course an Application Insights instance.

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

Typically only a jar/war/ear file is deployed to App Services. However, in order to install the Java agent, we'll need to bundle it (separately) with the application jar file. Luckily, App Services supports zip deployments, so all we need to do is to create a zip file that contains all the required resources. Note that the `app.jar` needs to be at the root of the zip file if you don't want to change the default settings. The final structure should look like this:

```bash
├── app.jar
└── resources
    ├── AI-Agent.xml
    └── applicationinsights-agent-2.5.1.jar
```

> Note that you can also put the agent at the top level, but it's best practice to put it in a specific directory together with its configuration.

There are multiple ways of creating the zip file, you could for example consider [Maven assembly](https://maven.apache.org/plugins/maven-assembly-plugin/index.html) plugin, command line tools such as `zip`, and even tasks as part of [Azure Devops](https://docs.microsoft.com/en-us/azure/devops/pipelines/tasks/utility/archive-files?view=azure-devops) or [Github Actions artifacts](https://github.com/actions/upload-artifact) etc. For the sake of simplicity and consistency, we'll be using [azure-web-app-maven](https://github.com/microsoft/azure-maven-plugins/wiki/Azure-Web-App) plugin. This plugin can create the bundled zip and also can handle the deployment.

There's also one more important piece of information that needs to be configured. The Application Insights agent jar needs to be specified as a startup option for the Java process. We can do that by setting the `JAVA_OPTS` application setting.

Again there's multiple ways of configuring the mentioned application setting, through the portal, ARM templates, Azure CLI etc. Fortunately the azure-web-app-maven plugin also supports configuring the application settings. So, all we need to do is provide the information in the right section of the `pom.xml`.

```xml
...
    <appSettings>
        <property>
            <name>JAVA_OPTS</name>
            <value>-javaagent:"D:/home/site/wwwroot/resources/applicationinsights-agent-${app.insights.version}.jar"</value>
        </property>
    </appSettings>
...
```

> A few remarks regarding the previous step, the zip deployment process unzips the contents of the zip file into the `$HOME/site/wwwroot` directory, and the javaagent option needs the absolute path for the agent jar. The included ARM template creates an App Services instance on a Windows plan, hence the prefix `D:/home`. For a Linux plan, you'd need to replace that with just `/home`. See the [Kudu docs](https://github.com/projectkudu/kudu/wiki/File-structure-on-azure) on the topic of the App Service File structure.

Now we're ready to deploy the zip file. All you need to do is pass the relevant information to run the `deploy` goal.

```bash
mvn azure-webapp:deploy -Dresource.group.name=$RG -Dwebapp.name=$WEBAPP -Dapp.insights.version=$APP_INSIGHTS_VERSION
```

The sample application is pretty basic, you can list registered employees by running the following command:

```bash
$ curl https://$WEBAPP.azurewebsites.net/api/employee
[]
```

The first time will return an empty list, you can insert new entries:

```bash
$ curl -X POST https://$WEBAPP.azurewebsites.net/api/employee -H "Content-Type: application/json" -d '{"alias":"meken", "firstName":"Murat", "lastName":"Eken"}'
{"id":1,"firstName":"Murat","lastName":"Eken","alias":"meken"}
```

After running a few of these you can tinker with Application Insights metrics/logs/dependency map to verify that the agent is sending telemetry to Application Insights. Note that it'll take a few minutes for the telemetry to be ingested by Application Insights, so if you don't immediately see anything, try it again after some time.
