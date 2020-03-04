# `@atomist/string-replace-skill`

<!---atomist-skill-readme:start--->

String Replace can be used to update configuration, code and text across files in all selected repositories. The string replacement will be run the next time there is a push to any of the selected repositories. If there are matches, this skill will create a pull request with the proposed changes.

## Configuration

### Name

Give your configuration of this skill a distinctive name so that you you will recognize it when you have more than one enabled. Something helpful like "Snake case → camel case for YAML" if, for example, your string replacement converts snake case to camel case on all YAML files.

### Replace in files

To restrict this files that String Replace will run on, provide one or more glob patterns. For example, to only run on YAML files with `.yaml` or `.yml` extensions at any depth in the repository, you could provide this glob pattern:

`*.yaml,*.yml`

For more information on glob patterns, see [the wikipedia page](https://en.wikipedia.org/wiki/Glob_(programming)).

### Substitution expression

Enter the expression to match and substitute. This will always start with `s/` and eng with `/g` which tells the skill to do a *global* replacement, replacing all matches found. ( `/g` is the only option supported). 

For example, to perform the snake case to camel case conversion, this substitution expression would accomplish the job:

`s/([a-zA-Z]*?)_([a-zA-Z])/$1\U$2/g`

For help crafting and testing your regular expressions, try [this online tool](https://regex101.com/) and see [this guide](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions/Cheatsheet).

### Which repositories

By default, this skill will be enabled for all repositories in all organizations you have connected.
To restrict the organizations or specific repositories on which the skill will run, you can explicitly
choose organization(s) and repositories.

## Integrations

**GitHub**

The Atomist GitHub integration must be configured to used this skill. At least one repository must be selected.

**Slack**

If the Atomist Slack integration is configured, this skill will send a notification message to the configured Slack channel when a pull request is created. 

You can invoke this skill from Slack. Entering this command in a channel with a linked repository will execute the string replacement defined in the skill configuration named "Snake case → camel case for YAML":

`@atomist string-replace --configuration="Snake case → camel case for YAML"`

You do need to run this command from a Slack channel that is linked to a repository. For more information about the Slack integration and channel repository linking, see [the documentation](https://docs.atomist.com/user/slack/).

## Related skills

This skill works well with the GitHub Notifications, Auto-merge pull request and Auto-rebase pull request skills.

<!---atomist-skill-readme:end--->

---

Created by [Atomist][atomist].
Need Help?  [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ (Atomist - How Teams Deliver Software)
[slack]: https://join.atomist.com/ (Atomist Community Slack) 
