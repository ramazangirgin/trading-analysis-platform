<script setup lang="ts">
import { NCheckbox, NCheckboxGroup, NSpace } from 'naive-ui'
import type { Permission } from '@/shared/api/types'
import { useLabels } from '@/shared/composables/useLabels'
import { permissionGroups } from '../model/permissionGroups'

defineProps<{ disabled?: boolean }>()
const selected = defineModel<Permission[]>({ required: true })

const { t } = useLabels()
const groups = permissionGroups()
</script>

<template>
  <NSpace vertical :size="12">
    <div v-for="group in groups" :key="group.area">
      <div class="area">{{ t(`permissions.groups.${group.area}`) }}</div>
      <NCheckboxGroup v-model:value="selected" :disabled="disabled">
        <NSpace vertical :size="4">
          <NCheckbox v-for="permission in group.permissions" :key="permission" :value="permission">
            {{ t(`permissions.keys.${permission}`) }}
            <code class="key">{{ permission }}</code>
          </NCheckbox>
        </NSpace>
      </NCheckboxGroup>
    </div>
  </NSpace>
</template>

<style scoped>
.area {
  font-weight: 600;
  margin-bottom: 4px;
}

.key {
  margin-left: 6px;
  opacity: 0.6;
  font-size: 12px;
}
</style>
