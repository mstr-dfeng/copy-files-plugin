/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt, Peter Hayes
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package copyFiles;

import hudson.*;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.tasks.*;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.util.*;

/**
 * Saves HTML reports for the project and publishes them.
 * 
 * @author Kohsuke Kawaguchi
 * @author Mike Rooney
 */
public class copyFilesBuildWrapper extends BuildWrapper {
    private final String masterFileDir;
    private final String masterFileName;
    private final String slaveFileDir;
    private final String masterRelativeTo;
    private final String slaveRelativeTo;

    @DataBoundConstructor
    public copyFilesBuildWrapper(String masterFileDir, String masterFileName, String slaveFileDir, String masterRelativeTo, String slaveRelativeTo) {
        this.masterFileDir = masterFileDir.trim();
        this.masterFileName = masterFileName.trim();
        this.slaveFileDir = slaveFileDir.trim();
        this.masterRelativeTo = masterRelativeTo;
        this.slaveRelativeTo = slaveRelativeTo;
    }

    public String getMasterFileDir() {
        return this.masterFileDir;
    }

    public String getMasterFileName() {
        return this.masterFileName;
    }

    public String getSlaveFileDir() {
        return this.slaveFileDir;
    }

    public String getMasterRelativeTo() {
        return this.masterRelativeTo;
    }

    public String getSlaveRelativeTo() {
        return this.slaveRelativeTo;
    }

    protected static String resolveParametersInString(AbstractBuild<?, ?> build, BuildListener listener, String input) {
        try {
            return build.getEnvironment(listener).expand(input);
        } catch (Exception e) {
            listener.getLogger().println("Failed to resolve parameters in string \""+
            input+"\" due to following error:\n"+e.getMessage());
        }
        return input;
    }

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        FilePath archiveDir = null;
        FilePath targetDir = null;
        if(getMasterRelativeTo().equals("UserContent")){
            archiveDir = Hudson.getInstance().getRootPath().child("userContent");
        }else if(getMasterRelativeTo().equals("Home")){
            archiveDir = Hudson.getInstance().getRootPath();
        }else if(getMasterRelativeTo().equals("MasterWorkspace")){
            archiveDir = new FilePath(new File(build.getProject().getRootDir(), "workspace"));
        }

        if(archiveDir != null){
            if(getMasterFileDir()!=null && getMasterFileDir().length()!=0){
                archiveDir = new FilePath(archiveDir, getMasterFileDir());
            }
        }
        else{
            //archiveDir = new FilePath(Hudson.getInstance().getChannel(),resolveParametersInString(build, listener, getMasterFileDir()));   Doesn't work both for remote and local build
            archiveDir = new FilePath(new File(resolveParametersInString(build, listener, getMasterFileDir())));
        }

        if(getSlaveRelativeTo().equals("SlaveWorkspace")){
            targetDir = build.getWorkspace();
            if(getSlaveFileDir()!=null && getSlaveFileDir().length()!=0){
                targetDir = new FilePath(targetDir, getSlaveFileDir());
            }
        }else if(getSlaveRelativeTo().equals("SlaveAnyDir")){
            //targetDir = new FilePath(build.getBuiltOn().getChannel(),resolveParametersInString(build, listener, getSlaveFileDir()));     Only work both for remote build
            targetDir = build.getBuiltOn().createPath(resolveParametersInString(build, listener, getSlaveFileDir()));
        }

        if (!archiveDir.exists()) {
            listener.error("[copy-files] Specified file directory '" + archiveDir + "' does not exist.");
            return NewEnvironment();
        }
        String[] fileNames = getMasterFileName().split(",");
        for(String fileName:fileNames){
            String file = fileName.trim();
            try {
                if (archiveDir.copyRecursiveTo(file, targetDir) == 0) {
                    listener.error("[copy-files] Directory '" + archiveDir + "' exists but failed copying '" + file +"' to '" + targetDir + "'.");
                    return NewEnvironment();
                }
                listener.getLogger().println("[copy-files] Copy file: '" + file +
                        "' from Master: " + archiveDir + " to Slave: " + targetDir);
            } catch (IOException e) {
                e.printStackTrace();
                listener.error("[copy-files] Fail to copy '" + file + "'.");
            }
        }

        return NewEnvironment();
    }

    public Environment NewEnvironment(){
        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
                // we need to return true so that the build can go on
                return true;
            }
        };
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public String getDisplayName() {
            // return Messages.JavadocArchiver_DisplayName();
            return "Copy files to slave before building";
        }

        public String getHelpFile() {
            return "/plugin/copy-files/help-projectConfig.html";
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }
    }
}
