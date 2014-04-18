package org.talend.geat.commands;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;
import org.talend.geat.GitConfiguration;
import org.talend.geat.GitUtils;
import org.talend.geat.InputsUtils;
import org.talend.geat.SanityCheck;
import org.talend.geat.SanityCheck.CheckLevel;
import org.talend.geat.exception.IllegalCommandArgumentException;
import org.talend.geat.exception.IncorrectRepositoryStateException;

import com.google.common.base.Strings;

/**
 * Command that starts a bug fix. Means:
 * <ul>
 * <li>fetch develop branch</li>
 * <li>create a local branch based on develop branch</li>
 * <li>checkout new bug branch</li>
 * </ul>
 */
public class BugStart extends Command {

    public static final String NAME = "bug-start";

    protected String           bugName;

    protected BugStart() {
        super();
    }

    public String getCommandName() {
        return NAME;
    }

    public Command parseArgs(String[] args) throws IllegalCommandArgumentException {
        if (args.length != 2) {
            throw IllegalCommandArgumentException.build(this);
        }
        bugName = args[1];

        return this;
    }

    public String getDescription() {
        return "Create a branch to work on a new bug fix";
    }

    public String getUsage() {
        return "<bug-name>";
    }

    @Override
    public CheckLevel getCheckLevel() {
        return CheckLevel.GIT_REPO_ONLY;
    }

    public void execute(Writer writer) throws IncorrectRepositoryStateException, IOException, GitAPIException {
        try {
            SanityCheck.check(getWorkingDir(), CheckLevel.NO_UNCOMMITTED_CHANGES);
        } catch (IncorrectRepositoryStateException e) {
            if (!InputsUtils.askUserAsBoolean(e.getDetails() + "\n\nProceed anyway")) {
                return;
            }
        }

        Git repo = Git.open(new File(getWorkingDir()));
        String bugBranchName = GitConfiguration.getInstance().get("bugPrefix") + "/" + bugName;
        boolean hasRemote = GitUtils.hasRemote("origin", repo.getRepository());

        // Test if such a branch exists locally:
        if (GitUtils.hasLocalBranch(repo.getRepository(), bugBranchName)) {
            throw new IncorrectRepositoryStateException("A local branch named '" + bugBranchName + "' already exist.");
        }

        if (hasRemote) {
            // Test if branch exist remotely:
            if (GitUtils.hasRemoteBranch(repo.getRepository(), bugBranchName)) {
                IncorrectRepositoryStateException irse = new IncorrectRepositoryStateException(
                        "A remote branch named '" + bugBranchName + "' already exist.");
                irse.addLine("To checkout this branch locally, use:");
                irse.addLine("");
                irse.addLine(Strings.repeat(" ", GitConfiguration.indentForCommandTemplates)
                        + "git fetch && git checkout " + bugBranchName);
                throw irse;
            }

            // git checkout master
            repo.checkout().setName(GitConfiguration.getInstance().get("bugStartPoint")).call();

            // git pull --rebase origin
            // 1. git fetch
            repo.fetch()
                    .setRefSpecs(
                            new RefSpec("refs/heads/" + GitConfiguration.getInstance().get("bugStartPoint")
                                    + ":refs/remotes/origin/" + GitConfiguration.getInstance().get("bugStartPoint")))
                    .setRemote("origin").call();
            // 2. git merge ff
            Ref refOriginMaster = repo.getRepository().getRef("origin/master");
            repo.merge().setFastForward(FastForwardMode.FF_ONLY).include(refOriginMaster).call();
        }

        repo.checkout().setCreateBranch(true).setStartPoint(GitConfiguration.getInstance().get("bugStartPoint"))
                .setName(bugBranchName).call();

        writer.write("Summary of actions:");
        writer.write(" - A new branch '" + bugBranchName + "' was created, based on '"
                + GitConfiguration.getInstance().get("bugStartPoint") + "'");
        writer.write(" - You are now on branch '" + bugBranchName + "'");
        writer.write("");
        writer.write("Now, start committing on your bug fix. When done, use:");
        writer.write("");
        writer.write(Strings.repeat(" ", GitConfiguration.indentForCommandTemplates) + "geat " + FeatureFinish.NAME
                + " " + bugName + " <policy>");
        writer.write("");
        writer.write("To share this branch, use:");
        writer.write("");
        writer.write(Strings.repeat(" ", GitConfiguration.indentForCommandTemplates) + "geat " + FeaturePush.NAME + " "
                + bugName);
    }

}
