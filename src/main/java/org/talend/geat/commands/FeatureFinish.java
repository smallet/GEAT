package org.talend.geat.commands;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.talend.geat.Configuration;
import org.talend.geat.GitConfiguration;
import org.talend.geat.GitUtils;
import org.talend.geat.InputsUtils;
import org.talend.geat.SanityCheck.CheckLevel;
import org.talend.geat.exception.IllegalCommandArgumentException;
import org.talend.geat.exception.IncorrectRepositoryStateException;
import org.talend.geat.exception.InterruptedCommandException;
import org.talend.geat.exception.NotRemoteException;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.Files;

/**
 * Command that finish a feature. Means:
 * <ul>
 * <li>fetch feature branch</li>
 * <li>fetch develop branch</li>
 * <li>merge feature branch into develop branch (differents policy can be applied here)</li>
 * <li>delete feature branch</li>
 * </ul>
 */
public class FeatureFinish extends Command {

    public static final String NAME = "feature-finish";

    protected enum MergePolicy {
        REBASE, SQUASH;
    }

    protected String      featureName;

    protected MergePolicy mergePolicy;

    protected FeatureFinish() {
        super();
    }

    public String getCommandName() {
        return NAME;
    }

    public String getDescription() {
        return "Merge and close a feature branch when work is finished";
    }

    public String getUsage() {
        return "<feature-name> [policy (squash|rebase), default="
                + GitConfiguration.getInstance().get("finishmergemode") + "]";
    }

    public Command parseArgs(String[] args) throws IllegalCommandArgumentException {
        if (args.length < 2) {
            throw IllegalCommandArgumentException.build(this);
        }
        featureName = args[1];

        try {
            if (args.length >= 3) {
                mergePolicy = MergePolicy.valueOf(args[2].toUpperCase());
            } else {
                mergePolicy = MergePolicy.valueOf(GitConfiguration.getInstance().get("finishmergemode").toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            StringBuilder sb = new StringBuilder();

            sb.append("Unknown merge policy '" + args[2] + "'");
            sb.append("Availables merge policy are:");
            for (MergePolicy current : MergePolicy.values()) {
                sb.append(" - " + current.name().toLowerCase());
            }
            throw new IllegalCommandArgumentException(sb.toString());
        }

        return this;
    }

    @Override
    public CheckLevel getCheckLevel() {
        return CheckLevel.NO_UNCOMMITTED_CHANGES;
    }

    public void execute(Writer writer) throws IncorrectRepositoryStateException, IOException, GitAPIException,
            InterruptedCommandException {
        Git repo = Git.open(new File(getWorkingDir()));
        String featureBranchName = GitConfiguration.getInstance().get("featurePrefix") + "/" + featureName;
        boolean hasRemote = GitUtils.hasRemote("origin", repo.getRepository());

        boolean continueAfterConflict = previouslyFinishingThisFeature(featureName, writer);

        // 1. Update sources from remote
        if (hasRemote) {
            try {
                GitUtils.callFetch(repo.getRepository(), GitConfiguration.getInstance().get("featureStartPoint"));
                GitUtils.callFetch(repo.getRepository(), featureBranchName);
            } catch (NotRemoteException e) {
                // We don't care
            }
        }

        // 2. Test if such a branch exists
        if (!GitUtils.hasLocalBranch(repo.getRepository(), featureBranchName)) {
            // TODO if continueAfterConflict==true, then we have an obsolete MERGE file, delete it

            IncorrectRepositoryStateException irse = new IncorrectRepositoryStateException("No local branch named '"
                    + featureBranchName + "'");
            irse.addLine("To see feature branches that may be finish, use:\n");
            irse.addLine(Strings.repeat(" ", Configuration.INSTANCE.getAsInt("geat.indentForCommandTemplates"))
                    + "git branch --list feature/*");
            throw irse;
        }

        // 3. Try to rebase feature branch
        if (mergePolicy == MergePolicy.REBASE) {
            if (!continueAfterConflict) {
                // git checkout feature/myfeature
                repo.checkout().setName(featureBranchName).call();
                // git rebase master
                RebaseResult rebaseResult = repo.rebase()
                        .setUpstream(GitConfiguration.getInstance().get("featureStartPoint")).call();

                if (rebaseResult.getStatus() == RebaseResult.Status.STOPPED) {
                    createMergeAbortedMarker();
                }
            }

            // git checkout master
            repo.checkout().setName(GitConfiguration.getInstance().get("featureStartPoint")).call();
            // re-init featureBranchName because we just changed it:
            Ref ref = repo.getRepository().getRef(featureBranchName);
            // git merge feature/myfeature
            repo.merge().setFastForward(FastForwardMode.FF_ONLY).include(ref).call();
        } else if (mergePolicy == MergePolicy.SQUASH) {
            if (!continueAfterConflict) {
                // git checkout master
                repo.checkout().setName(GitConfiguration.getInstance().get("featureStartPoint")).call();
                // git merge --squash feature/myfeature
                Ref ref = repo.getRepository().getRef(featureBranchName);
                MergeResult mergeResult = repo.merge().setSquash(true).include(ref).call();

                if (mergeResult.getMergeStatus() == MergeStatus.CONFLICTING) {
                    createMergeAbortedMarker();
                }

                String msg = InputsUtils.askUser("Commit message", "Finish feature " + featureName);
                repo.commit().setMessage(msg).call();
            }
        }

        // 4. Remove feature branch
        repo.branchDelete().setBranchNames(featureBranchName).setForce(mergePolicy == MergePolicy.SQUASH).call();

        writer.write("Summary of actions:");
        if (hasRemote) {
            writer.write(" - New commits from 'origin/" + GitConfiguration.getInstance().get("featureStartPoint")
                    + "' has been pulled");
        }
        writer.write(" - The feature branch '" + featureBranchName + "' was rebased into '"
                + GitConfiguration.getInstance().get("featureStartPoint") + "'");
        writer.write(" - Feature branch '" + featureBranchName + "' has been removed");
        writer.write(" - You are now on branch '" + GitConfiguration.getInstance().get("featureStartPoint") + "'");
        if (hasRemote) {
            writer.write("");
            writer.write("Now, your new feature is ready to be pushed. To do this, use:");
            writer.write("");
            writer.write(Strings.repeat(" ", Configuration.INSTANCE.getAsInt("geat.indentForCommandTemplates"))
                    + "git push origin " + GitConfiguration.getInstance().get("featureStartPoint"));
        }
    }

    private boolean previouslyFinishingThisFeature(String featureName, Writer writer) throws IOException,
            InterruptedCommandException {
        File mergeMarker = new File(getGeatConfigFolfer(), "MERGE");
        if (mergeMarker.exists()) {
            String readFirstLine = Files.readFirstLine(mergeMarker, Charsets.UTF_8);
            String[] split = readFirstLine.split(" ");
            if (split.length == 4 && split[0].equals("MERGE") && split[2].equals("IN")) {
                if (split[1].equals(featureName)) {
                    mergeMarker.delete();
                    return true;
                } else {
                    InterruptedCommandException ice = new InterruptedCommandException("Previous finish of " + split[1]
                            + " was aborted because of conflicts.");
                    ice.addLine("Please finish this feature first.");
                    ice.addLine("You can then complete the finish by running it again.\n");
                    ice.addLine(Strings.repeat(" ", Configuration.INSTANCE.getAsInt("geat.indentForCommandTemplates"))
                            + "geat " + FeatureFinish.NAME + " " + split[1] + " <policy>");
                    throw ice;
                }
            } else {
                // Malformed MERGE file:
                writer.write("WARN: previous file " + mergeMarker.getAbsolutePath() + " is malformed.");
                writer.write(Strings.repeat(" ", Configuration.INSTANCE.getAsInt("geat.indentForCommandTemplates"))
                        + readFirstLine);
                writer.write("Deleting the file");
                mergeMarker.delete();
                return false;
            }
        } else {
            return false;
        }
    }

    private void createMergeAbortedMarker() throws IOException, InterruptedCommandException {
        File mergeMarker = new File(getGeatConfigFolfer(), "MERGE");
        Files.createParentDirs(mergeMarker);
        Files.touch(mergeMarker);
        Files.write(
                ("MERGE " + featureName + " IN " + GitConfiguration.getInstance().get("featureStartPoint")).getBytes(),
                mergeMarker);

        InterruptedCommandException ice = new InterruptedCommandException(
                "There were merge conflicts. To see more details, use:\n");
        ice.addLine(Strings.repeat(" ", Configuration.INSTANCE.getAsInt("geat.indentForCommandTemplates"))
                + "git status");
        ice.addLine("");
        ice.addLine("When resolved, you can then complete the finish by running it again.\n");
        ice.addLine(Strings.repeat(" ", Configuration.INSTANCE.getAsInt("geat.indentForCommandTemplates")) + "geat "
                + FeatureFinish.NAME + " " + featureName + " " + mergePolicy.toString().toLowerCase());
        throw ice;
    }

    private File getGeatConfigFolfer() {
        File folder = new File(new File(getWorkingDir(), ".git"), ".geat");
        if (!folder.exists()) {
            folder.mkdir();
        }
        return folder;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    public MergePolicy getMergePolicy() {
        return mergePolicy;
    }

    public void setMergePolicy(MergePolicy mergePolicy) {
        this.mergePolicy = mergePolicy;
    }

}
