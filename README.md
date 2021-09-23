## Sign cookies lambda

project/plugins.sbt
### Running Locally
There is a `LambdaRunner` class with some example json. To run this, you will need to set the environment variables to the same values as the integration lambda. You can get these by running this command with integration credentials

`aws lambda get-function --function-name tdr-sign-cookies-intg --query  'Configuration.Environment.Variables'  
`

You will also need to set and environment variable called `AWS_LAMBDA_FUNCTION_NAME` with the value `tdr-sign-cookies-intg` This is set automatically by the lambda but has to be set manually here.

You will need to make sure that you have integration credentials set before running `LambdaRunner`, either by setting them in `~/.aws/credentials` or by setting environment variables in the run configuration. You will need permissions to access KMS keys for this to work.
