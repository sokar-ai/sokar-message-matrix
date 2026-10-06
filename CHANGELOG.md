# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

Initial public version.

### Changed

- Every change is checked with `sokar-release`: no page or source cites an issue, and every page
  under `doc/` is in the site's navigation exactly once. A change to documents alone runs only these
  checks and the tests that read a document, not the build.
