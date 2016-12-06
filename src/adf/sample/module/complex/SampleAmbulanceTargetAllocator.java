package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.AmbulanceTargetAllocator;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class SampleAmbulanceTargetAllocator extends AmbulanceTargetAllocator {
    private Map<EntityID, EntityID> result;

    private Collection<EntityID> civilians;
    private Collection<EntityID> agents;

    private Collection<EntityID> rescuedHuman;

    public SampleAmbulanceTargetAllocator(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.result = new HashMap<>();
        this.civilians = new HashSet<>();
        this.agents = new HashSet<>();
        this.rescuedHuman = new HashSet<>();
    }

    @Override
    public Map<EntityID, EntityID> getResult() {
        return this.result;
    }

    @Override
    public AmbulanceTargetAllocator calc() {
        return this;
    }

    @Override
    public AmbulanceTargetAllocator updateInfo(MessageManager messageManager) {
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {

        }
        return this;
    }
}
