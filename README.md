# ESPD Backend Serverless

Note: Do not mix up appName (Function Name) in build.gradle between master and develop branches. 
The same rule applies for GitHub actions deployment scripts. The correct values are the following:

master=promitheus-espd(stage)
develop=promitheus-espd-develop

## Deployment process ##
This repository is meant to trigger (manually or automatically) the deployment actions for the ESPD v2 system.

### Develop depolyment ###
When a new commit is pushed to the develop branch of this repository then the `depoly-to-develop` action is automatically executed.

The `deploy-to-develop` action could also be executed manually from the "Actions" tab in GitHub, where the develop branch should be selected as the workflow source.

This deployment script clones the external dependency repository `espd-vcd-system` so that the project is build and published to local Maven. Then it checks-out the project to build Azure Functions package. Finally it deploys the project to Azure using
the declared publish profile.
