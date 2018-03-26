package adf.sample.tactics;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.debug.WorldViewLauncher;
import adf.component.centralized.CommandPicker;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.TargetAllocator;
import adf.component.tactics.TacticsFireStation;
import rescuecore2.worldmodel.EntityID;

import java.util.Map;

public class SampleTacticsFireStation extends TacticsFireStation
{
    private TargetAllocator allocator;
    private CommandPicker picker;
	private Boolean isVisualDebug;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData debugData)
    {
        messageManager.setChannelSubscriber(moduleManager.getChannelSubscriber("MessageManager.CenterChannelSubscriber", "adf.sample.module.comm.SampleChannelSubscriber"));
        messageManager.setMessageCoordinator(moduleManager.getMessageCoordinator("MessageManager.CenterMessageCoordinator", "adf.sample.module.comm.SampleMessageCoordinator"));

        switch (scenarioInfo.getMode())
        {
            case PRECOMPUTATION_PHASE:
            case PRECOMPUTED:
                this.allocator = moduleManager.getModule(
                        "TacticsFireStation.TargetAllocator",
                        "adf.sample.module.complex.SampleFireTargetAllocator");
                this.picker = moduleManager.getCommandPicker(
                        "TacticsFireStation.CommandPicker",
                        "adf.sample.centralized.CommandPickerFire");
                break;
            case NON_PRECOMPUTE:
                this.allocator = moduleManager.getModule(
                        "TacticsFireStation.TargetAllocator",
                        "adf.sample.module.complex.SampleFireTargetAllocator");
                this.picker = moduleManager.getCommandPicker(
                        "TacticsFireStation.CommandPicker",
                        "adf.sample.centralized.CommandPickerFire");

        }
        registerModule(this.allocator);
        registerModule(this.picker);

        this.isVisualDebug = (scenarioInfo.isDebugMode()
                && moduleManager.getModuleConfig().getBooleanValue("VisualDebug", false));
    }

    @Override
    public void think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData debugData)
    {
        modulesUpdateInfo(messageManager);

        if (isVisualDebug)
        {
            WorldViewLauncher.getInstance().showTimeStep(agentInfo, worldInfo, scenarioInfo);
        }
        Map<EntityID, EntityID> allocatorResult = this.allocator.calc().getResult();
        for (CommunicationMessage message : this.picker.setAllocatorResult(allocatorResult).calc().getResult())
        {
            messageManager.addMessage(message);
        }
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData debugData)
    {
        modulesResume(precomputeData);

        if (isVisualDebug)
        {
            WorldViewLauncher.getInstance().showTimeStep(agentInfo, worldInfo, scenarioInfo);
        }
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData debugData)
    {
        modulesPreparate();

        if (isVisualDebug)
        {
            WorldViewLauncher.getInstance().showTimeStep(agentInfo, worldInfo, scenarioInfo);
        }
    }
}
