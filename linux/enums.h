#pragma once

#include <QMetaType>
#include <QHash>

namespace AirpodsTrayApp
{
    namespace Enums
    {
        Q_NAMESPACE

        enum class NoiseControlMode : quint8
        {
            Off = 0,
            NoiseCancellation = 1,
            Transparency = 2,
            Adaptive = 3,

            MinValue = Off,
            MaxValue = Adaptive,
        };
        Q_ENUM_NS(NoiseControlMode)

        enum class AirPodsModel
        {
            Unknown,
            AirPods1,
            AirPods2,
            AirPods3,
            AirPodsPro,
            AirPodsPro2Lightning,
            AirPodsPro2USBC,
            AirPodsMax
        };
        Q_ENUM_NS(AirPodsModel)

        // Get model enum from model number
        inline AirPodsModel parseModelNumber(const QString &modelNumber) {
            QHash<QString, AirPodsModel> modelNumberMap = {
                {"A1523", AirPodsModel::AirPods1},
                {"A1722", AirPodsModel::AirPods1},
                {"A2032", AirPodsModel::AirPods2},
                {"A2031", AirPodsModel::AirPods2},
                {"A2084", AirPodsModel::AirPodsPro},
                {"A2083", AirPodsModel::AirPodsPro},
                {"A2096", AirPodsModel::AirPodsMax},
                {"A2565", AirPodsModel::AirPods3},
                {"A2564", AirPodsModel::AirPods3},
                {"A3047", AirPodsModel::AirPodsPro2USBC},
                {"A3048", AirPodsModel::AirPodsPro2USBC},
                {"A3049", AirPodsModel::AirPodsPro2USBC},
                {"A2931", AirPodsModel::AirPodsPro2Lightning},
                {"A2699", AirPodsModel::AirPodsPro2Lightning},
                {"A2698", AirPodsModel::AirPodsPro2Lightning}};

            return modelNumberMap.value(modelNumber, AirPodsModel::Unknown);
        }

        // Return icons based on model
        inline QPair<QString, QString> getModelIcon(AirPodsModel model) {
            switch (model) {
                case AirPodsModel::AirPods1:
                case AirPodsModel::AirPods2:
                case AirPodsModel::AirPods3:
                    return {"pod.png", "pod_case.png"};
                case AirPodsModel::AirPodsPro:
                case AirPodsModel::AirPodsPro2Lightning:
                case AirPodsModel::AirPodsPro2USBC:
                    return {"podpro.png", "podpro_case.png"};
                case AirPodsModel::AirPodsMax:
                    return {"max.png", "max_case.png"};
                default:
                    return {"pod.png", "pod_case.png"}; // Default icon for unknown models
            }
        }

    }
}