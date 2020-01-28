# `@atomist/string-replace-skill`

## Problem

As a developer, I want to make bulk replacement of strings across many files in many repos, so that I can easily do things like rename projects, update copyright notices and other common tasks.

## What it does

The user specifies a glob file pattern (e.g. `*.rb` ) to apply a string replacement to, and a string replacement regular expression (e.g. `s/<match>/replace/`). When a push happens on a repo, the string replacement skill will be triggered.

## Example

Using this skill to keep this date synced with the last Commit:  wrong2

## Configuration

| Name                   | Value        | Type   | Required | Notes |
| :---                   | :----        | :----  | :---  | :------ | 
| Files to apply replace | Glob Pattern | `Text` | false | By default, if no glob pattern provided, the string replacement will run on all files in the repo |
| String replacement pattern | String Replacement (e.g. s/(([c]\)\s*)2019/$12020/ig ) | `Text` | true |  |
| Scope | Selected GitHub Organization(s) and/or Repositories | `Org & Repo Selection` | false | By default, scope will include all organizations and repos available in the workspace  |

---

Created by [Atomist][atomist].
Need Help?  [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ (Atomist - How Teams Deliver Software)
[slack]: https://join.atomist.com/ (Atomist Community Slack) 
