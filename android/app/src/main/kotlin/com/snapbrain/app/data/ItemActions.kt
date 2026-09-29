package com.snapbrain.app.data

import com.snapbrain.core.Action
import com.snapbrain.core.ExtractJson
import com.snapbrain.core.actionOf
import java.time.ZoneId

/** v2 items keep up to three actions; items stored before v2 still have their single action_type/payload. */
fun ItemEntity.actionList(zone: ZoneId = ZoneId.systemDefault()): List<Action> =
    if (actions != null) {
        ExtractJson.decodeActions(actions).mapNotNull { actionOf(it.type, it.payload, zone) }
    } else {
        listOfNotNull(actionOf(actionType, actionPayload, zone))
    }
