package javaposse.jobdsl.dsl

import com.google.common.collect.Lists

import javaposse.jobdsl.dsl.helpers.AuthorizationContextHelper
import javaposse.jobdsl.dsl.helpers.BuildParametersContextHelper
import javaposse.jobdsl.dsl.helpers.MavenHelper
import javaposse.jobdsl.dsl.helpers.MultiScmContextHelper
import javaposse.jobdsl.dsl.helpers.ScmContextHelper
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContextHelper
import javaposse.jobdsl.dsl.helpers.step.StepContextHelper
import javaposse.jobdsl.dsl.helpers.toplevel.TopLevelHelper
import javaposse.jobdsl.dsl.helpers.triggers.TriggerContextHelper
import javaposse.jobdsl.dsl.helpers.wrapper.WrapperContextHelper

import java.util.logging.Level
import java.util.logging.Logger

/**
 * DSL Element representing a Jenkins Build Pipeline
 *
 * @author jrydberg
 */
public class PipelineStage {
    private static final Logger LOGGER = Logger.getLogger(PipelineStage.getName());

    JobManagement jobManagement
    List<Job> jobs;
    String projectName;
    String name;
    int totalJobs;

    public PipelineStage(JobManagement jobManagement, int totalJobs, String name) {
        this.jobManagement = jobManagement;
        this.jobs = Lists.newArrayList()
	def jobName = jobManagement.getParameters().get("JOB_NAME");
	this.projectName = jobName.size() > 0 ? jobName.split("_")[0] : jobName;
        this.name = name;
        this.totalJobs = totalJobs;
    }

    private String makeJobName(int i) {
    	String.format("%s_%02d", projectName, totalJobs + i);
    }

    public Job task(String name, Closure closure) {
        LOGGER.log(Level.FINE, "Got closure and have ${jobManagement}")
        Job job = new Job(jobManagement)

	job.name(makeJobName(jobs.size() + 1))
	job.displayName(String.format("%s (%s %s)", job.name, this.name, name))
        if (totalJobs > 0 && jobs.size() == 0) {
            // The fist task in a stage needs to import the artifacts from the last
            // build of the previous stage
            job.steps {
                copyArtifacts(makeJobName(jobs.size()), '**/*.jar', '', true, true) {
                    upstreamBuild()
                }
            }
        }
        job.with(closure)

        def node = new NodeBuilder()
        def attr = [plugin: 'delivery-pipeline-plugin@0.6.9']
        def prop = node.'se.diabol.jenkins.pipeline.PipelineProperty'(attr) {
            stageName owner.name
            taskName name
        }
        job.configure { project ->
            (project / 'properties') << prop
        }
        
        // Save jobs, so that we know what to extract XML from
 	jobs.push(job)
	return job
    }

    public void chain() {
        jobs.size > 1 && jobs[1..-1].eachWithIndex { dn, i ->
            def up = jobs[i]
            up.publishers {
                downstream(dn.name)
            }
            dn.wrappers {
                runOnSameNodeAs(up.name, true)
            }
        }
    }
}
