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
public class Pipeline {
    private static final Logger LOGGER = Logger.getLogger(Pipeline.getName());

    JobManagement jobManagement
    List<Job> jobs;
    String projectName;

    public Pipeline(JobManagement jobManagement) {
        this.jobManagement = jobManagement;
        this.jobs = Lists.newArrayList()
	def jobName = jobManagement.getParameters().get("JOB_NAME");
	this.projectName = jobName.size() > 0 ? jobName.split("_")[0] : jobName;
    }

    private String makeJobName(int i) {
    	String.format("%s_%02d", projectName, i);
    }

    public Job stage(String name, Closure closure) {
        LOGGER.log(Level.FINE, "Got closure and have ${jobManagement}")
        Job job = new Job(jobManagement)

	job.name(makeJobName(jobs.size() + 1))
	job.displayName(String.format("%s (%s)", projectName, name))
        job.with(closure)

        // Save jobs, so that we know what to extract XML from
 	jobs.push(job)
	return job
    }

    public void chain() {
        for (int i = 0; i < jobs.size(); i++) {
            def job = jobs[i]
	    // pass on workspace from upstream to downstream jobs
            // includeGlob, excludeGlob, critera, method, overrideDefaultExcludes
	    job.publishers {
	        publishCloneWorkspace("**/*", "", "Any", "TAR", true)
	    }
	    // clone stuff from previous job
	    job.scm {
	        cloneWorkspace(makeJobName(i)); 
	    }
	}

        for (int i = 1; i < jobs.size(); i++) {
            def up = jobs[i - 1]
            def dn = jobs[i]

	    // trigger downstream job on success
	    up.publishers {
	        downstream(dn.name)
// 	        downstreamParameterized {
// 	            trigger(dn.name) {
// 		        gitRevision()
//                         //subversionRevision()
//                         currentBuild()
//                     }
// 	        }
            }
        }
    }
}
