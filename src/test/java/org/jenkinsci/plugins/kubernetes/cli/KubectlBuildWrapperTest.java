package org.jenkinsci.plugins.kubernetes.cli;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Shell;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.envinject.EnvInjectBuildWrapper;
import org.jenkinsci.plugins.envinject.EnvInjectJobPropertyInfo;
import org.jenkinsci.plugins.kubernetes.cli.helpers.DummyCredentials;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Max Laverse
 */
public class KubectlBuildWrapperTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testEnvVariablePresent() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), DummyCredentials.secretCredential("test-credentials"));

        FreeStyleProject p = r.createFreeStyleProject();

        KubectlBuildWrapper bw = new KubectlBuildWrapper();
        bw.credentialsId = "test-credentials";
        p.getBuildWrappersList().add(bw);

        Shell builder = new Shell("#!/bin/bash\nenv");
        p.getBuildersList().add(builder);

        FreeStyleBuild b = p.scheduleBuild2(0).waitForStart();

        assertNotNull(b);
        r.assertBuildStatus(Result.SUCCESS, r.waitForCompletion(b));
        r.assertLogContains("KUBECONFIG=", b);
    }

    @Test
    public void testEnvInterpolation() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), DummyCredentials.secretCredential("test-credentials"));

        FreeStyleProject p = r.createFreeStyleProject();

        EnvInjectJobPropertyInfo info = new EnvInjectJobPropertyInfo(null,
                "SERVER_URL=http://my-server",
                null,
                null,
                true,
                null);
        p.getBuildWrappersList().add(new EnvInjectBuildWrapper(info));


        KubectlBuildWrapper bw = new KubectlBuildWrapper();
        bw.credentialsId = "test-credentials";
        bw.serverUrl = "${SERVER_URL}";
        p.getBuildWrappersList().add(bw);

        Shell b2 = new Shell("#!/bin/bash\ncat \"$KUBECONFIG\"");
        p.getBuildersList().add(b2);

        FreeStyleBuild b = p.scheduleBuild2(0).waitForStart();

        assertNotNull(b);
        r.assertBuildStatus(Result.SUCCESS, r.waitForCompletion(b));
        r.assertLogContains("server: \"http://my-server\"", b);
    }

    @Test
    public void testKubeConfigDisposed() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), DummyCredentials.secretCredential("test-credentials"));

        FreeStyleProject p = r.createFreeStyleProject();

        KubectlBuildWrapper bw = new KubectlBuildWrapper();
        bw.credentialsId = "test-credentials";
        p.getBuildWrappersList().add(bw);

        FreeStyleBuild b = p.scheduleBuild2(0).waitForStart();

        assertNotNull(b);
        r.assertBuildStatus(Result.SUCCESS, r.waitForCompletion(b));
        r.assertLogContains("kubectl configuration cleaned up", b);
    }

    @Test
    public void testListedCredentials() throws Exception {
        CredentialsStore store = CredentialsProvider.lookupStores(r.jenkins).iterator().next();
        store.addCredentials(Domain.global(), DummyCredentials.usernamePasswordCredential("1"));
        store.addCredentials(Domain.global(), DummyCredentials.secretCredential("2"));
        store.addCredentials(Domain.global(), DummyCredentials.fileCredential("3"));
        store.addCredentials(Domain.global(), DummyCredentials.certificateCredential("4"));
        store.addCredentials(Domain.global(), DummyCredentials.tokenCredential("5"));

        KubectlBuildWrapper.DescriptorImpl d = new KubectlBuildWrapper.DescriptorImpl();
        FreeStyleProject p = r.createFreeStyleProject();
        ListBoxModel s = d.doFillCredentialsIdItems(p.asItem(), "");

        assertEquals(6, s.size());
    }

    @Test
    public void testConfigurationPersistedOnSave() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), DummyCredentials.secretCredential("test-credentials"));

        FreeStyleProject p = r.createFreeStyleProject();

        KubectlBuildWrapper bw = new KubectlBuildWrapper();
        bw.credentialsId = "test-credentials";
        p.getBuildWrappersList().add(bw);

        assertEquals("<?xml version='1.1' encoding='UTF-8'?>\n" +
                "<project>\n" +
                "  <keepDependencies>false</keepDependencies>\n" +
                "  <properties/>\n" +
                "  <scm class=\"hudson.scm.NullSCM\"/>\n" +
                "  <canRoam>false</canRoam>\n" +
                "  <disabled>false</disabled>\n" +
                "  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>\n" +
                "  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>\n" +
                "  <triggers/>\n" +
                "  <concurrentBuild>false</concurrentBuild>\n" +
                "  <builders/>\n" +
                "  <publishers/>\n" +
                "  <buildWrappers>\n" +
                "    <org.jenkinsci.plugins.kubernetes.cli.KubectlBuildWrapper>\n" +
                "      <credentialsId>test-credentials</credentialsId>\n" +
                "    </org.jenkinsci.plugins.kubernetes.cli.KubectlBuildWrapper>\n" +
                "  </buildWrappers>\n" +
                "</project>", p.getConfigFile().asString());
    }
}
