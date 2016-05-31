package adf.challenge.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.challenge.SampleModuleKey;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.Search;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class SampleSearch extends Search {

    private int clusterIndex;
    private Collection<EntityID> unexploredBuildings;
    private EntityID result;

    public SampleSearch(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager) {
        super(ai, wi, si, moduleManager);
        this.unexploredBuildings = new HashSet<>();
        this.clusterIndex = -1;
    }

    @Override
    public Search updateInfo(MessageManager messageManager) {
        if(this.clusterIndex != -1) {
            if(this.unexploredBuildings.isEmpty()) {
                this.initUnexploredBuildings();
            }
            for (EntityID next : this.worldInfo.getChanged().getChangedEntities()) {
                this.unexploredBuildings.remove(next);
            }
            if(this.unexploredBuildings.isEmpty()) {
                this.initUnexploredBuildings();
            }
        }
        else {
            Clustering clustering = null;
            if (this.agentInfo.me().getStandardURN() == StandardEntityURN.AMBULANCE_TEAM) {
                clustering = this.moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING);
            } else if (this.agentInfo.me().getStandardURN() == StandardEntityURN.FIRE_BRIGADE) {
                clustering = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING);
            } else if (this.agentInfo.me().getStandardURN() == StandardEntityURN.POLICE_FORCE) {
                clustering = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_CLUSTERING);
            }
            if(clustering != null) {
                this.clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
                this.initUnexploredBuildings(clustering);
                for (EntityID next : this.worldInfo.getChanged().getChangedEntities()) {
                    this.unexploredBuildings.remove(next);
                }
                if(this.unexploredBuildings.isEmpty()) {
                    this.initUnexploredBuildings(clustering);
                }
            }
        }
        return this;
    }

    private void initUnexploredBuildings() {
        Clustering clustering = null;
        if(this.agentInfo.me().getStandardURN() == StandardEntityURN.AMBULANCE_TEAM) {
            clustering = this.moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING);
        } else if(this.agentInfo.me().getStandardURN() == StandardEntityURN.FIRE_BRIGADE) {
            clustering = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING);
        } else if(this.agentInfo.me().getStandardURN() == StandardEntityURN.POLICE_FORCE) {
            clustering = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_CLUSTERING);
        }
        this.initUnexploredBuildings(clustering);
    }

    private void initUnexploredBuildings(Clustering clustering) {
        for (StandardEntity next : clustering.getClusterEntities(this.clusterIndex)) {
            if(StandardEntityURN.BUILDING.equals(next.getStandardURN())) {
                this.unexploredBuildings.add(next.getID());
            }
        }
    }

    @Override
    public Search calc() {
        PathPlanning pathPlanning = null;
        if(this.agentInfo.me().getStandardURN() == StandardEntityURN.AMBULANCE_TEAM) {
            pathPlanning = this.moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING);
        } else if(this.agentInfo.me().getStandardURN() == StandardEntityURN.FIRE_BRIGADE) {
            pathPlanning = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);
        } else if(this.agentInfo.me().getStandardURN() == StandardEntityURN.POLICE_FORCE) {
            pathPlanning = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
        }
        if(pathPlanning != null) {
            List<EntityID> path = pathPlanning.setFrom(this.agentInfo.getPosition()).setDestination(this.unexploredBuildings).calc().getResult();
            if (path != null) {
                this.result = path.get(path.size() - 1);
            }
        }
        return this;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public Search precompute(PrecomputeData precomputeData) {
        return this;
    }

    @Override
    public Search resume(PrecomputeData precomputeData) {
        return this;
    }

    @Override
    public Search preparate() {
        return this;
    }
}