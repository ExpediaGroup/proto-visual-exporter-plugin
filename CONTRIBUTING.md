## Adding additional visual exporters
1. Implement the ProtoSchemaExporter interface.
1. Update the *ProtoVisualExporterPlugin* with the arguments for the new visual exporter.

## Testing
We are using the Salesforce Jprotoc plugin to generate a *descriptor_dump* from protos which the plugin can then consume to test.
It does the same thing as what protoc plugin will do.

## Bugs
We use Github Issues for our bug reporting. Please make sure the bug isn't already listed before
opening a new issue.

## Enhancement Requests
If there is a feature that you would like added, please open an issue in GitHub and follow the respective
template.

## Development
All work on proto-schema-exporter happens directly on Github. Project leadership will review opened pull requests. See [README.md](README.md) for instructions on how to build, test and document your PR.


## Contributing to Documentation
To contribute to documentation, you can directly modify the corresponding .md files in the
docs directory. Please submit a pull request.

## License
By contributing to this project, you agree that your contributions will be licensed
under its Apache License.
