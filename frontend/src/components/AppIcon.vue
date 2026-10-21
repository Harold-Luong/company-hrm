<script setup>
defineProps({ name: { type: String, required: true }, size: { type: Number, default: 20 } })
const paths = {
    book: 'M12 5v16 M12 5C9 3 5 3 3 4v15c3-1 6-1 9 2 3-3 6-3 9-2V4c-2-1-6-1-9 1z',
    calendar: 'M4 5h16v16H4z M4 10h16 M8 3v4 M16 3v4 M8 14h2 M14 14h2 M8 17h2',
    user: 'M20 21v-2a7 7 0 0 0-14 0v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8',
    add: 'M15 21v-2a6 6 0 0 0-12 0v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M19 8v6 M16 11h6',
    shield: 'M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7z M8 12l3 3 5-6',
    arrow: 'M5 12h14 M14 7l5 5-5 5',
    logout: 'M9 3H4v18h5 M9 12h12 M16 7l5 5-5 5',
    menu: 'M4 6h16 M4 12h16 M4 18h16',
    close: 'm6 6 12 12 M18 6 6 18',
    lock: 'M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4 M12 14v3',
    mail: 'M3 5h18v14H3z m0 0 9 8 9-8',
    eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12 M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6',
    check: 'm5 12 4 4L19 6',
    info: 'M12 16v-4 M12 8h.01 M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20',
    clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M12 6v6l4 2',
    language:
        'M12 2a10 10 0 1 0 0 20a10 10 0 0 0 0-20 M2 12h20 M12 2c-3 3-4 6-4 10s1 7 4 10 M12 2c3 3 4 6 4 10s-1 7-4 10',
}
</script>

<template>
    <svg
:width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
        stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path :d="paths[name] || paths.info" />
    </svg>
</template>
