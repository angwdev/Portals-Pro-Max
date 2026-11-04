# Contributing to Portals Pro Max

Just generally sick to the coding style and layout style you see in the current project, it will just speed up pull requests. Useful requests will still be accepted if the style is wrong though will take longer as they will need to be changed by me or another key programmer to the project (if there are more at the time of requesting)

Just make sure to fill out the pull request template properly. If one of the fields doesnt apply just remove it or put None as the only bullet point.

## Commits and pull requests
Please ensure that your commits are in the following style for PR's

https://www.conventionalcommits.org/en/v1.0.0/

Also if you could, please run `pre-commit run --files ...` or `pre-commit run --all-files` to ensure that the code is formatted correctly.
You will need to have clang-format installed for this to work.

## Types available
* **build**: Changes that affect the build system or external dependencies
* **ci**: Changes to our CI configuration files and scripts
* **docs**: Documentation only changes
* **feat**: A new feature
* **fix**: A bug fix
* **perf**: A code change that improves performance
* **refactor**: A code change that neither fixes a bug nor adds a feature
* **style**: Changes that do not affect the meaning of the code (white-space, formatting, missing semi-colons, etc)
* **test**: Adding missing tests or correcting existing tests

## Scopes available
The scopes available should be the specific modules being worked on. E.g. core, paper, docs

## Documentation
The documentation of the original plugin lives on the [website](https://github.com/sekwah41/Advanced-Portals/tree/website) branch, most tags and commands are the same. The new ones are described in the README.

At some point I may merge this onto this branch to try to push for documentation changes with new features, though for now this works.

## CI
If you make any changes to the CI make sure that you test it locally first.

GitHub Actions (`.github/workflows/build.yml`) runs the tests, builds the plugin and starts a Paper server with it. The Codeberg snapshots can be tested locally via `woodpecker-cli exec --pipeline-event push .woodpecker/snapshot.yml`.

Ensure docker is set up locally and the woodpecker cli is installed.

If you are testing on an arm system you may need to set `DOCKER_DEFAULT_PLATFORM=linux/amd64`
