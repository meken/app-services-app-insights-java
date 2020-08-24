# Azure App Services, Application Insights and Java agent

If you're running your Java app on Azure App Services and you'd like to get deeper insights, without code changes, on your application dependencies, logs etc. you might want to consider the [Azure Application Insights Java Agent](https://docs.microsoft.com/en-us/azure/azure-monitor/app/java-agent).

In principle, it's sufficient to include a reference to the agent to get things rolling. However, currently App Services doesn't provide a standard way of including the Applicaton Insights Java agent in its configuration, so the responsibilty of uploading that lies with the developers. This is an example repository of how to bundle and configure the Application Insights Java agent as part of the development process.

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

If you've cloned this repo, change directory to the ```app``` folder and run the following command to build the Spring Boot jar file. The pom file uses the passed variable to resolve the Application Insights jar dependency.

```bash
mvn clean package -Dapp.insights.version=$APP_INSIGHTS_VERSION
```

The above command also downloads and copies the necessary dependencies for the Java agent. These end up in the `target` directory, under the `resources` subdirectory.

As mentioned in the introduction, we'll need to package the application jar file as well as the agent jar file (and its configuration) into a single zip file.

> There are multiple ways of generating the zip file, you could use the Maven assembly plugin, or a built-in archive task if you're dealing with Azure DevOps, Github Actions, Jenkins etc. For the sake of simplicity we'll be using the command line tool `zip` for this purpose. Note that this utility might not be installed on your OS, so you might want to install that before continuing.

The zip file needs to have a specific structure, namely the application jar has to be in the root directory. The easiest way of doing that is running the command from the `target` directory.

```bash
cd target
zip -r deployment.zip app.jar resources/
```

Now we've got the deployment package, we need to do one more step before the deployment. The application insights agent jar needs to be specified as a startup option for the Java process. We can do that by setting the `JAVA_OPTS` environment variable.

```bash
AGENT=-javaagent:\"D:/home/site/wwwroot/resources/applicationinsights-agent-$APP_INSIGHTS_VERSION.jar\"
az webapp config appsettings set -g $RG -n $WEBAPP --settings JAVA_OPTS=$AGENT -o none
```

> A few remarks regarding the previous step, the zip deployment process unzips the contents of the zip file into the `$HOME/site/wwwroot` directory, and the javaagent option needs the absolute path for the agent jar. The included ARM template creates an App Services instance on a Windows plan, hence the prefix `D:/home`, for a Linux plan, you'd need to replace that with just `/home`. See the [Kudu docs](https://github.com/projectkudu/kudu/wiki/File-structure-on-azure) on the topic of the App Service File structure.

Now we're ready to deploy the zip file. Assuming that you're still in the `target` directory, just run the following command

```bash
az webapp deployment source config-zip -g $RG -n $WEBAPP --src deployment.zip
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

After running a few of these you can tinker with Application Insights metrics/logs/dependency map to verify that the agent is basically sending information.
