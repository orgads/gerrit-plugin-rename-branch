load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "rename-branch",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: rename-branch",
        "Gerrit-Module: com.googlesource.gerrit.plugins.renamebranch.PluginModule",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.renamebranch.SshModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
)

junit_tests(
    name = "rename-branch_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["rename-branch"],
    deps = [
        ":rename-branch__plugin_test_deps",
    ],
)

java_library(
    name = "rename-branch__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":rename-branch__plugin",
    ],
)
