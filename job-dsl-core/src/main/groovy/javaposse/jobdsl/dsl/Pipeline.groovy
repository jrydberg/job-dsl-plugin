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
    List<PipelineStage> stages;
    String projectName;
    int totalJobs = 0;
    
    public Pipeline(JobManagement jobManagement) {
        this.jobManagement = jobManagement;
        this.stages = Lists.newArrayList()
    }

    public PipelineStage stage(String name, Closure closure) {
        LOGGER.log(Level.FINE, "Got closure and have ${jobManagement}")

        PipelineStage stage = new PipelineStage(jobManagement, totalJobs, name)
        stage.with(closure)
        totalJobs += stage.jobs.size()

        // Save stages, we chain them together later
 	stages.push(stage)
	return stage
    }

    public void chain() {
        stages.each { it.chain() }
        
        stages.size > 1 && stages[1..-1].eachWithIndex { dn, i ->
            def up = stages[i]
            up.jobs[-1].publishers {
                downstream(dn.jobs[0].name, dn.manual ? 'MANUAL' : 'SUCCESS')
                archiveArtifacts {
                    pattern up.getArtifacts() ?: '**'
                    allowEmpty true
                }
            }
        }
    }
}
