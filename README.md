# `@atomist/string-replace-skill`

String Replace can be used to update configuration, code and text across files in all selected repositories. 
The string replacement will be run the next time there is a push to any of the selected repositories. 
If there are matches, this skill will create a pull request with the proposed changes.

<!---atomist-skill-readme:start--->

# What it's useful for

Make updates to code and configuration across your entire codebase. Automatically update files across all selected repositories based on a regular expression.

* Update configuration, code, documentation or any file in your repositories
* Rename packages across an entire codebase
* Update information needing periodic revision like dates, licenses, comment blocks

# Before you get started

Connect and configure these integrations:

1. **GitHub**
2. **Slack** (optional)

This skill raises pull requests. The GitHub integration must be configured in order to use this skill. At least one repository must be selected.

When the optional Slack integration is enabled, users can run String Replace directly from Slack.
 
# How to configure

1. **Select the files to scan**

To restrict the files that this skill will run on, provide one or more [glob patterns](https://en.wikipedia.org/wiki/Glob_(programming)). 
    For example, to only run on YAML files with `.yaml` or `.yml` extensions at any depth in the repository, 
    you would provide this glob pattern:
    
    `*.yaml,*.yml`
    
The glob pattern is optional.  If not specified, the expression will run on all of the files in the selected repositories.

![regular expression](docs/image/screenshot1.png)

2. **Enter a substitution expression**

Enter the expression to match and substitute. This will always start with `s/` and eng with `/g` which tells the skill to do a *global* replacement, replacing all matches found. ( `/g` is the only option supported). 

For example, to perform the snake case to camel case conversion, this substitution expression would accomplish the job:

`s/([a-zA-Z]*?)_([a-zA-Z])/$1\U$2/g`

For help crafting and testing your regular expressions, try [this online tool](https://regex101.com/) and see [this guide](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions/Cheatsheet).

3. **Select branch policy**
 
Select whether the skill should run on pushes to the default branch only, or all branches. 

![schedule](docs/image/screenshot3.png)


4. **Set to run on a schedule**

![schedule](docs/image/screenshot2.png)

To run on a schedule, enter a [cron schedule](https://en.wikipedia.org/wiki/Cron). If no cron schedule is set, the
skill runs on every `Push` to a selected repository.

When run on a schedule, this skill only runs on the default branch.

---

## How to use this skill

Use this skill to run a String Replace operation against any of your repos 
whenever one of the following occurs: 

* A set of Commits is pushed to a Repository
* A regularly scheduled event like "once per day", "every Tuesday", "first day of each month", etc.
* A user runs the `@atomist string-replace` command in a Slack channel of which `@atomist` is a member

The String Replace operations are declared using regular expression. If the string replace operation results in a change to one or more files in a repository, a pull request with the changes will be raised.


Try to start with the strings you want to capture.  Perhaps you hear a request like:

> Someone should write a bot that updates the year 
> on your Clojure project's readme so projects that still work don't look old.

Create a configuration of this skill representing this requirement.  For help crafting 
and testing your regular expressions, try [this online tool](https://regex101.com/) 
and see [this guide](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions/Cheatsheet).

Combining capture groups and back references can be surprisingly powerful.  A yaml snake case to camel case converter like:

`s/([a-zA-Z]*?)_([a-zA-Z])/$1\U$2/g`

could be applied to all files matching the [glob patterns](https://en.wikipedia.org/wiki/Glob_(programming)) `*.yaml,*.yml` and then triggered from slack 
by typing:

```
`@atomist string-replace --configuration="Snake case → camel case for YAML"`
``` 

where `configuration` is a name of one of your skill's configurations.  This skill will only create pull requests.  It
will never add a Commit to an existing branch ref.

Use the repo filter to limit the skill to a select set of repositories.  However, useful search replace operations can
be easily shared too.  Just let the skill see a larger set of Repositories.  Some operations are useful on _all_ of your
repositories.

<!---atomist-skill-readme:end--->

---

Created by [Atomist][atomist].
Need Help?  [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ (Atomist - How Teams Deliver Software)
[slack]: https://join.atomist.com/ (Atomist Community Slack) 
