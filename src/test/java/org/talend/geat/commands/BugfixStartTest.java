package org.talend.geat.commands;

import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Assert;
import org.junit.Test;
import org.talend.geat.GitConfiguration;
import org.talend.geat.GitUtils;
import org.talend.geat.JUnitUtils;
import org.talend.geat.exception.IllegalCommandArgumentException;
import org.talend.geat.exception.IncorrectRepositoryStateException;
import org.talend.geat.exception.InterruptedCommandException;
import org.talend.geat.io.DoNothingWriter;

public class BugfixStartTest extends Bug21Test {

    @Override
    protected Command initCommandInstance() {
        return new BugfixStart();
    }

    @Test
    public void testParseArgsOk1() throws IllegalCommandArgumentException, IncorrectRepositoryStateException {
        BugfixStart command = (BugfixStart) CommandsRegistry.INSTANCE.getCommand(BugfixStart.NAME).parseArgs(
                new String[] { BugfixStart.NAME, "myBug" });
        Assert.assertEquals("myBug", command.bugName);
        Assert.assertNull(command.startPoint);
    }

    @Test
    public void testParseArgsOk2() throws IllegalCommandArgumentException, IncorrectRepositoryStateException {
        BugfixStart command = (BugfixStart) CommandsRegistry.INSTANCE.getCommand(BugfixStart.NAME).parseArgs(
                new String[] { BugfixStart.NAME, "myBug", "startpoint" });
        Assert.assertEquals("myBug", command.bugName);
        Assert.assertEquals("startpoint", command.startPoint);
    }

    @Test
    public void testParseArgsWrongNumberArgs1() throws IllegalCommandArgumentException,
            IncorrectRepositoryStateException {
        thrown.expect(IllegalCommandArgumentException.class);
        CommandsRegistry.INSTANCE.getCommand(BugfixStart.NAME).parseArgs(
                new String[] { BugfixStart.NAME, "myBug", "anotherParam", "oneMoreParam" });
    }

    @Test
    public void testParseArgsWrongNumberArgs2() throws IllegalCommandArgumentException,
            IncorrectRepositoryStateException {
        thrown.expect(IllegalCommandArgumentException.class);
        CommandsRegistry.INSTANCE.getCommand(BugfixStart.NAME).parseArgs(new String[] { BugfixStart.NAME });
    }

    @Test
    public void testExecuteMaster() throws GitAPIException, IOException, IllegalCommandArgumentException,
            IncorrectRepositoryStateException, InterruptedCommandException {
        Git git = JUnitUtils.createTempRepo();
        JUnitUtils.createInitialCommit(git, "file1");
        Assert.assertFalse(GitUtils.hasLocalBranch(git.getRepository(), "bugfix/master/tagada"));
        CommandsRegistry.INSTANCE.getCommand(BugfixStart.NAME)
                .parseArgs(new String[] { BugfixStart.NAME, "tagada", "master" }).setWriter(new DoNothingWriter())
                .run();
        Assert.assertTrue(GitUtils.hasLocalBranch(git.getRepository(), "bugfix/master/tagada"));
    }

    @Test
    public void testExecuteMaintenance() throws GitAPIException, IOException, IllegalCommandArgumentException,
            IncorrectRepositoryStateException, InterruptedCommandException {
        Git git = JUnitUtils.createTempRepo();
        JUnitUtils.createInitialCommit(git, "file1");
        git.branchCreate().setName("maintenance/5.4").call();
        Assert.assertFalse(GitUtils.hasLocalBranch(git.getRepository(), "bugfix/5.4/tagada"));
        CommandsRegistry.INSTANCE.getCommand(BugfixStart.NAME)
                .parseArgs(new String[] { BugfixStart.NAME, "tagada", "maintenance/5.4" })
                .setWriter(new DoNothingWriter()).run();
        Assert.assertTrue(GitUtils.hasLocalBranch(git.getRepository(), "bugfix/5.4/tagada"));
    }

    @Test
    public void testExecuteRelease() throws GitAPIException, IOException, IllegalCommandArgumentException,
            IncorrectRepositoryStateException, InterruptedCommandException {
        Git git = JUnitUtils.createTempRepo();
        JUnitUtils.createInitialCommit(git, "file1");
        git.branchCreate().setName("release/5.4.2").call();
        Assert.assertFalse(GitUtils.hasLocalBranch(git.getRepository(), "bugfix/5.4.2/tagada"));
        CommandsRegistry.INSTANCE.getCommand(BugfixStart.NAME)
                .parseArgs(new String[] { BugfixStart.NAME, "tagada", "release/5.4.2" })
                .setWriter(new DoNothingWriter()).run();
        Assert.assertTrue(GitUtils.hasLocalBranch(git.getRepository(), "bugfix/5.4.2/tagada"));
    }

    @Test
    public void testExecuteBasicDiffStartPoint() throws GitAPIException, IOException, IllegalCommandArgumentException,
            IncorrectRepositoryStateException, InterruptedCommandException {
        Git git = JUnitUtils.createTempRepo();
        JUnitUtils.createInitialCommit(git, "file1");
        git.branchCreate().setName("maintenance/1.0").call();
        Assert.assertFalse(GitUtils.hasLocalBranch(git.getRepository(), "bugfix/1.0/tagada"));
        CommandsRegistry.INSTANCE.getCommand(BugfixStart.NAME)
                .parseArgs(new String[] { BugfixStart.NAME, "tagada", "maintenance/1.0" })
                .setWriter(new DoNothingWriter()).run();
        Assert.assertTrue(GitUtils.hasLocalBranch(git.getRepository(), "bugfix/1.0/tagada"));

        Assert.assertEquals("maintenance/1.0", GitConfiguration.getInstance().get("bugfixStartPoint"));
    }

    @Test
    public void testExecuteBranchAlreadyExist() throws GitAPIException, IOException, IllegalCommandArgumentException,
            IncorrectRepositoryStateException, InterruptedCommandException {
        thrown.expect(IncorrectRepositoryStateException.class);
        Git git = JUnitUtils.createTempRepo();
        JUnitUtils.createInitialCommit(git, "file1");
        Assert.assertFalse(GitUtils.hasLocalBranch(git.getRepository(), "bugfix/master/tagada"));

        git.branchCreate().setName("bugfix/master/tagada").call();
        Assert.assertTrue(GitUtils.hasLocalBranch(git.getRepository(), "bugfix/master/tagada"));

        CommandsRegistry.INSTANCE.getCommand(BugfixStart.NAME)
                .parseArgs(new String[] { BugfixStart.NAME, "tagada", "master" }).setWriter(new DoNothingWriter())
                .run();
    }

}
