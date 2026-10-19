<script setup lang="ts">
import { computed, h } from 'vue'
import { NSelect, NTooltip, type SelectOption } from 'naive-ui'
import type { Role } from '@/shared/api/types'

const props = defineProps<{ roles: Role[] }>()
const selected = defineModel<string[]>({ required: true })

const options = computed<SelectOption[]>(() =>
  props.roles.map((role) => ({
    label: role.name,
    value: role.id,
    description: role.description,
  })),
)

function renderOption({ node, option }: { node: ReturnType<typeof h>; option: SelectOption }) {
  const description = option.description
  return typeof description === 'string' && description
    ? h(NTooltip, { placement: 'left' }, { trigger: () => node, default: () => description })
    : node
}
</script>

<template>
  <NSelect v-model:value="selected" multiple :options="options" :render-option="renderOption" />
</template>
