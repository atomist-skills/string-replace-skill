# `@atomist/string-replace-skill`

On every Push, run a search and replace regular expression on a select set of files and open a Pull Request with any changes.  This skill
also supports a Slack integration to request the search and replace operation from Slack.

## Usage

Each configuration of this skill should define a search replace expression and a glob expression to define which files
should be included in the search.

| Name         | Type   | Required | Notes |
| :---         | :----  | :---  | :------ | 
| glob-pattern | `Text` | false | By default, if no glob pattern provided, the string replacement will run on all files in the repo |
| expression   | `Text` | true |  |
| Scope | `Org & Repo Selection` | true | Choose the scope of the String-Replace Operation |

Each configuration can configure a different set of relevant Repositories.  Scopes can also overlap, but if multiple expressions
match, separate Pull Requests will be opened (one for each expression).

## Example

Search all html files in the repository for the regix `search`.  Globally replace occurrences of this pattern with the string `replace`.

* `glob-pattern=**/*.html`
* `expression=s/search/replace/g`

## Integrations

* `GitHub` - requires the Atomist GitHub application installed in some Orgs
* `Slack` - if the Atomist slack app is installed, this skill will notify the linked Slack channel 
            whenever a PR is raised for a search and replace expression.
            
## Collaborations

This skill works well with the `GitHub Notifications`, `GitHub Auto-merge pull request`, and `GitHub Auto-rebase pull request` skills.

## Slack Commands

This skill also adds a new command to your Slack team:

`@atomist sed --configuration=example1`

If you run this command from a Slack channel, with a linked Repo, it will lookup a regular expression and glob-pattern
from the `config-name` of this skill.  As long as this configuration exists, the skill will apply the search and replace operation, 
and then open a PR if there are any changes.

---

Created by [Atomist][atomist].
Need Help?  [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ (Atomist - How Teams Deliver Software)
[slack]: https://join.atomist.com/ (Atomist Community Slack) 
